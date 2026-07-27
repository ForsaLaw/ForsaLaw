#!/usr/bin/env python3
#
# Collecte HORS LIGNE du corpus juridique tunisien vers un repertoire de transit.
#
#   python scripts/harvest-corpus.py cassation --out ../corpus-staging
#   python scripts/harvest-corpus.py jurisite  --out ../corpus-staging
#   python scripts/harvest-corpus.py cassation --out ../corpus-staging --themes TA,TB --max 20
#
# POURQUOI UN SCRIPT SEPARE, ET PAS UN JOB SPRING :
#   il s'agit d'une reprise initiale ponctuelle (~5 300 PDF, ~2 Go, ~2 h), pas d'une
#   veille incrementale. La sortir de l'application permet de RELIRE ET VALIDER le corpus
#   avant qu'il n'atteigne la base. Le job Spring (a la JortIngestionJob) viendra ensuite,
#   pour le seul flux incrementiel, et lira ce meme format de manifeste.
#
# CE QUE LE SCRIPT NE FAIT PAS :
#   il n'ecrit rien en base et ne calcule aucun embedding. Il depose des fichiers bruts et
#   un manifeste JSONL. L'ingestion reste la responsabilite du backend.
#
# INTEGRITE DES CHIFFRES — LE POINT CRITIQUE :
#   dans un texte arabe (droite a gauche), les suites de chiffres ressortent parfois
#   INVERSEES a l'extraction (47234 lu « 43274 »). Le defaut est SILENCIEUX et
#   INCONSTANT : certains documents sortent juste, d'autres non. On ne peut donc ni le
#   detecter par sondage, ni le corriger en inversant tout.
#   La parade est structurelle : les METADONNEES (numero, date, sommaire) proviennent du
#   TABLEAU HTML, jamais du PDF. Le PDF ne fournit que le corps du texte. Chaque document
#   porte en plus un champ `digit_check` comparant le numero officiel a ce que contient
#   reellement le texte extrait. Voir docs/CORPUS_MANIFEST.md.
#
# DEPENDANCE : pypdf   (pip install pypdf)
#
# CIVILITE : ce sont des services publics. User-Agent identifiable, requetes espacees,
#   reprise sur incident plutot que rafales de reessais. Ne pas descendre --delay sous 1 s.

import argparse
import hashlib
import html
import http.client
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone

try:
    from pypdf import PdfReader
except ImportError:
    sys.exit("pypdf manquant : pip install pypdf")

UA = "ForsaLawBot/1.0 (+https://forsalaw.tn; contact@forsalaw.tn)"

CASSATION = "http://www.cassation.tn/"          # HTTP seul : le port 443 est ferme.
FIQH_PATH = "%D9%81%D9%82%D9%87-%D8%A7%D9%84%D9%82%D8%B6%D8%A7%D8%A1/"
JURISITE = "https://www.jurisitetunisie.com/tunisie/codes/"

# Les 17 matieres du moteur « فقه القضاء ». Un arret peut figurer sous plusieurs
# matieres : la deduplication se fait sur le numero de decision.
THEMES = {
    "TA": "مدني عام", "TB": "تجاري", "TC": "شخصي", "TD": "اجتماعي",
    "TF": "جزائي", "TG": "اجراءات جزائية", "TH": "اجراءات مدنية", "TI": "تحكيم",
    "VT": "بيع", "LC": "أكرية", "MR": "عيني", "UR": "استعجالي",
    "AS": "تأمين وحوادث مرور", "MS": "إجراءات جماعية", "TJ": "قانون دولي خاص",
    "CR": "قرارات الدوائر المجتمعة", "PC": "التناسب - الفصل 49 من الدستور",
}

# Codes Jurisite. `coc` est absent de carte_codes.htm mais bien en ligne : ne jamais
# reconstruire cette liste depuis la page d'index seule.
CODES = [
    "coc", "cp", "cpp", "cpcc", "ct", "csp", "cc", "cs", "cdr", "cdip",
    "celect", "cde", "cpresse", "national", "cdet", "cdpf", "cirppis", "tva",
    "flocal", "catu", "assurance", "chydro", "copc", "telecom", "cii",
    "patri", "cr", "cspri", "poste", "veteri", "ccl",
    "constitution", "Constitution_2014", "Constitution_2022",
]

# Base DCAF (Geneve) : WordPress + WPML, texte integral servi en JSON par l'API REST.
# Aucune extraction PDF, donc AUCUN risque d'inversion de chiffres — c'est la source la
# plus sure du corpus. Couvre la strate decrets / arretes / circulaires, absente ailleurs.
LEGSEC = "https://legislation-securite.tn"
LEGSEC_API = LEGSEC + "/wp-json/wp/v2"

# robots.txt impose « Crawl-delay: 10 » : on ne descend jamais en dessous.
LEGSEC_MIN_DELAY = 10.0

# Taxonomies utiles au filtrage RAG. `status-categories` porte « en-vigueur » /
# « abroge » : indispensable pour ne pas servir du droit abroge comme droit en vigueur.
LEGSEC_TAXONOMIES = [
    "text-type-categories", "institution-categories", "status-categories",
    "thematic-folders-categories", "legislations-categories", "database-index-categories",
]

# Certaines fiches ne portent pas le texte mais un avis d'indisponibilite. Elles doivent
# etre marquees : ingerees telles quelles, elles pollueraient le corpus avec des chunks
# qui n'ont aucun contenu juridique.
LEGSEC_PLACEHOLDERS = (
    "disponible uniquement en langue arabe",
    "n'est pas encore publiée au JORT",
    "n’est pas encore publiée au JORT",
    "متوفر باللغة العربية فقط",
)

MIN_CHARS_PER_PAGE = 80   # identique a forsalaw.rag.ingestion.min-chars-per-page

# Marqueurs d'EN-TETE d'article. Jurisite melange les conventions d'un code a l'autre :
# le COC ecrit « Art. 191. - » (1 318 fois) et « ART. 116. - » (9 fois), le Code penal
# « Article premier ». Ne compter que « Article \d » sous-estimait le corpus d'un tiers.
#
# VOLONTAIREMENT SENSIBLE A LA CASSE : en ignorant la casse, « conformement a l'article 5 »
# — une simple reference dans le corps du texte — serait compte comme un en-tete, ce qui
# gonflait la mesure de ~60 %. On ne veut ici que les DEBUTS d'article.
#
# Mesure de couverture, pas de decoupage : le decoupage reste l'affaire de
# LegalArticleChunker cote backend.
# NB : le texte doit etre DESECHAPPE (html.unescape) avant comptage. Jurisite ecrit
# « Article&nbsp;5 » ; sur le HTML brut, « Article » et « 5 » sont separes par la chaine
# litterale « &nbsp; » et aucun \s ne correspond. Le Code du travail ajoute un point —
# « Article.&nbsp;10&nbsp;: » — d'ou le \.? ci-dessous.
ARTICLE_MARKER = re.compile(
    r"\b(?:Articles?\.?\s+(?:premier|\d)|ARTICLES?\.?\s+(?:PREMIER|\d)"
    r"|Art\.\s*\d|ART\.\s*\d"
    r"|الفصل\s*[0-9٠-٩]|المادة\s*[0-9٠-٩])")


# ---------------------------------------------------------------- transport

class Fetcher:
    """Client HTTP minimal : User-Agent identifiable, temporisation, reessais bornes."""

    def __init__(self, delay=1.5, retries=3, timeout=90):
        self.delay, self.retries, self.timeout = delay, retries, timeout
        self.opener = urllib.request.build_opener()
        self.opener.addheaders = [("User-Agent", UA), ("Accept-Language", "ar,fr;q=0.8")]
        self._last = 0.0

    def _wait(self):
        gap = time.monotonic() - self._last
        if gap < self.delay:
            time.sleep(self.delay - gap)
        self._last = time.monotonic()

    def get(self, url, binary=False):
        return self._do(urllib.request.Request(url), binary)

    def post_multipart(self, url, fields, referer):
        b = "----WebKitFormBoundary" + uuid.uuid4().hex[:16]
        body = b"".join(
            f'--{b}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
            for k, v in fields.items()
        ) + f"--{b}--\r\n".encode()
        req = urllib.request.Request(url, data=body, headers={
            "Content-Type": f"multipart/form-data; boundary={b}",
            "Referer": referer,
        })
        return self._do(req, binary=False)

    def _do(self, req, binary):
        last = None
        for attempt in range(self.retries):
            self._wait()
            try:
                raw = self.opener.open(req, timeout=self.timeout).read()
                return raw if binary else raw.decode("utf-8", "replace")
            # http.client.IncompleteRead (reponse tronquee) descend de HTTPException, PAS
            # de OSError ni de URLError : sans cette branche, un seul fichier tronque fait
            # tomber toute une collecte de plusieurs milliers de documents.
            except (urllib.error.URLError, urllib.error.HTTPError,
                    http.client.HTTPException, OSError) as e:
                last = e
                # Recul progressif : un service public qui flanche ne se martele pas.
                time.sleep(self.delay * (2 ** attempt))
        raise RuntimeError(f"echec apres {self.retries} tentatives : {req.full_url} ({last})")


# ---------------------------------------------------------------- manifeste

class Manifest:
    """JSONL en ajout seul. Relu au demarrage pour rendre la collecte reprenable."""

    def __init__(self, path):
        self.path = path
        self.seen = set()
        if os.path.exists(path):
            with open(path, encoding="utf-8") as fh:
                for line in fh:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        self.seen.add(json.loads(line)["key"])
                    except (json.JSONDecodeError, KeyError):
                        continue  # ligne tronquee par une interruption : on l'ignore
        self.fh = open(path, "a", encoding="utf-8")

    def add(self, rec):
        self.fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
        self.fh.flush()          # flush a chaque ligne : une coupure ne doit rien perdre
        self.seen.add(rec["key"])

    def close(self):
        self.fh.close()


# ---------------------------------------------------------------- PDF

def inspect_pdf(path, official_number):
    """Applique le meme seuil que PdfTextExtractor, puis controle l'integrite du numero.

    `digit_check` vaut :
      ok        le numero officiel figure tel quel dans le texte extrait ;
      reversed  seul son miroir figure  -> inversion bidi confirmee sur ce document ;
      absent    ni l'un ni l'autre      -> a verifier manuellement.
    """
    try:
        reader = PdfReader(path)
        pages = len(reader.pages)
        text = "".join((p.extract_text() or "") for p in reader.pages).strip()
    except Exception as e:                                   # PDF corrompu ou chiffre
        return {"pages": 0, "chars": 0, "arabic_chars": 0, "text_layer": False,
                "digit_check": "error", "error": f"{type(e).__name__}: {e}"}

    digits = re.sub(r"\D", "", official_number or "")
    if not digits:
        check = "absent"
    elif digits in text:
        check = "ok"
    elif digits[::-1] in text:
        check = "reversed"
    else:
        check = "absent"

    return {
        "pages": pages,
        "chars": len(text),
        "arabic_chars": sum(1 for c in text if "؀" <= c <= "ۿ"),
        "text_layer": len(text) >= max(1, pages) * MIN_CHARS_PER_PAGE,
        "digit_check": check,
    }


def nom_court(nom, limite=90):
    """Nom de fichier sur : Windows plafonne un chemin complet a 260 caracteres.

    Les intitules officiels depassent allegrement la limite (« Loi n° 2007-50 du
    23 juillet 2007 modifiant et completant la Loi n° 2001-36 ... »), et l'ecriture
    echoue alors avec FileNotFoundError. On tronque en gardant un condense court pour
    garantir l'unicite ; l'intitule integral reste dans le manifeste, champ `titre`.
    """
    base, ext = os.path.splitext(nom)
    propre = re.sub(r"[^\w.() -]", "_", base).strip() or "document"
    if len(propre) <= limite:
        return propre + ext
    digest = hashlib.sha256(nom.encode("utf-8")).hexdigest()[:8]
    return f"{propre[:limite]}_{digest}{ext}"


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for blk in iter(lambda: fh.read(1 << 20), b""):
            h.update(blk)
    return h.hexdigest()


def now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


# ---------------------------------------------------------------- cassation

def read_form(fetcher):
    """Recupere les jetons caches du formulaire.

    TYPO3 signe `__referrer[*]` et `__trustedProperties`. Ils doivent etre relus sur une
    page fraiche : un jeton perime renvoie 403. Ni cookie ni JavaScript ne sont requis.
    """
    page = fetcher.get(CASSATION + FIQH_PATH)
    m = re.search(r'<form enctype="multipart/form-data" name="search".*?</form>', page, re.S)
    if not m:
        raise RuntimeError("formulaire « فقه القضاء » introuvable : la page a change.")
    form = m.group(0)
    action = html.unescape(re.search(r'action="([^"]+)"', form).group(1))
    fields = {}
    for i in re.finditer(r'<input[^>]*name="([^"]+)"[^>]*value="([^"]*)"', form):
        name = html.unescape(i.group(1))
        if "submit" not in name.lower():
            fields[name] = html.unescape(i.group(2))
    return action, fields


ROW_RE = re.compile(r"<tr>\s*<td[^>]*>(.*?)</td>\s*<td[^>]*>(.*?)</td>\s*"
                    r"<td[^>]*>(.*?)</td>\s*<td[^>]*>(.*?)</td>\s*</tr>", re.S)


def parse_rows(page, theme):
    """Extrait les lignes du tableau de resultats.

    C'est ICI que naissent les metadonnees de reference : numero et date y sont dans le
    bon ordre, contrairement au texte du PDF.
    """
    out = []
    body = re.search(r'<table class="filter">.*?<tbody>(.*?)</tbody>', page, re.S)
    if not body:
        return out
    for num_td, date_td, subj_td, pdf_td in ROW_RE.findall(body.group(1)):
        strip = lambda s: html.unescape(re.sub(r"<[^>]+>", "", s)).strip()
        number = strip(num_td)
        date_raw = strip(date_td)
        pdf_m = re.search(r'href="([^"]+\.pdf)"', pdf_td, re.I)
        if not (number and pdf_m):
            continue
        detail = re.search(r'href="([^"]+)"', subj_td)
        iso = ""
        d = re.match(r"(\d{2})\.(\d{2})\.(\d{4})$", date_raw)
        if d:
            iso = f"{d.group(3)}-{d.group(2)}-{d.group(1)}"
        out.append({
            "doc_number": number,
            "case_number": number.split(".")[0],
            "date": iso,
            "date_raw": date_raw,
            "headnote": strip(subj_td),
            "theme": theme,
            "theme_label": THEMES.get(theme, theme),
            "detail_url": urllib.parse.urljoin(CASSATION, html.unescape(detail.group(1))) if detail else "",
            "pdf_url": urllib.parse.urljoin(CASSATION, html.unescape(pdf_m.group(1))),
        })
    return out


def harvest_cassation(args):
    out = os.path.join(args.out, "cassation")
    os.makedirs(os.path.join(out, "pdf"), exist_ok=True)
    fetcher = Fetcher(args.delay)
    man = Manifest(os.path.join(out, "manifest.jsonl"))
    themes = [t.strip() for t in args.themes.split(",")] if args.themes else list(THEMES)

    print(f"Reprise : {len(man.seen)} arret(s) deja au manifeste.", flush=True)
    action, hidden = read_form(fetcher)

    # 1) Recensement : un POST par matiere, puis deduplication sur le numero.
    index = {}
    for theme in themes:
        fields = dict(hidden)
        for k, v in (("shkeyword", ""), ("shdocdate1", ""), ("shdocdate2", ""),
                     ("shdocnum", ""), ("shtheme", theme)):
            fields[f"tx_uploadexample_piexample[search][{k}]"] = v
        try:
            page = fetcher.post_multipart(CASSATION + action, fields, CASSATION + FIQH_PATH)
        except RuntimeError as e:
            print(f"  {theme} : ECHEC recensement ({e})", file=sys.stderr, flush=True)
            continue
        rows = parse_rows(page, theme)
        added = 0
        for r in rows:
            key = r["doc_number"]
            if key in index:
                index[key]["also_themes"].append(theme)   # arret multi-matieres
            else:
                r["also_themes"] = []
                index[key] = r
                added += 1
        print(f"  {theme} {THEMES.get(theme,''):<24} {len(rows):>5} ligne(s), {added:>5} nouveau(x)",
              flush=True)

    todo = [r for k, r in index.items() if k not in man.seen]
    if args.max:
        todo = todo[:args.max]
    print(f"\n{len(index)} arret(s) distinct(s) ; {len(todo)} a telecharger.\n", flush=True)
    if args.dry_run:
        man.close()
        return

    # 2) Telechargement + controle d'integrite, un document a la fois.
    stats = {"ok": 0, "scanned": 0, "reversed": 0, "absent": 0, "error": 0}
    for i, rec in enumerate(todo, 1):
        name = re.sub(r"[^\w.-]", "_", urllib.parse.unquote(os.path.basename(rec["pdf_url"])))
        path = os.path.join(out, "pdf", name)
        try:
            blob = fetcher.get(rec["pdf_url"], binary=True)
        except RuntimeError as e:
            print(f"[{i}/{len(todo)}] {rec['doc_number']} ECHEC {e}", file=sys.stderr, flush=True)
            stats["error"] += 1
            continue
        with open(path, "wb") as fh:
            fh.write(blob)

        info = inspect_pdf(path, rec["case_number"])
        rec.update(info)
        rec.update({
            "key": rec["doc_number"], "source": "cassation",
            "pdf_path": os.path.relpath(path, args.out).replace("\\", "/"),
            "bytes": len(blob), "sha256": sha256(path), "fetched_at": now(),
        })
        man.add(rec)

        if not info["text_layer"]:
            stats["scanned"] += 1
        elif info["digit_check"] == "reversed":
            stats["reversed"] += 1
        elif info["digit_check"] == "absent":
            stats["absent"] += 1
        else:
            stats["ok"] += 1

        if i % 25 == 0 or i == len(todo):
            print(f"[{i}/{len(todo)}] {stats['ok']} ok · {stats['reversed']} inverses · "
                  f"{stats['absent']} a verifier · {stats['scanned']} scannes · "
                  f"{stats['error']} echecs", flush=True)

    man.close()
    print(f"\nTermine. {stats}")
    if stats["reversed"] or stats["absent"]:
        print("ATTENTION : des numeros ne concordent pas avec le texte extrait.\n"
              "  Les metadonnees du manifeste (issues du HTML) restent fiables ;\n"
              "  ne jamais relire le numero ou la date depuis le corps du PDF.")


# ---------------------------------------------------------------- jurisite

# Le sommaire ne s'appelle pas partout pareil : csp utilise « Menu.html », ccl
# « menua.html », les constitutions « menup.html » ou « menu2014.html ». On essaie donc
# les variantes connues plutot que de supposer « menu.html ».
ENTRY_CANDIDATES = ["menu.html", "Menu.html", "menua.html", "menup.html",
                    "menu2014.html", "menu.htm", "index.html"]


def find_entry(fetcher, base):
    for name in ENTRY_CANDIDATES:
        try:
            fetcher.get(base + name)
            return name
        except RuntimeError:
            continue
    return None


def harvest_jurisite(args):
    """Aspire l'arborescence HTML des codes.

    On conserve le HTML BRUT : le decoupage en articles appartient a
    LegalArticleChunker cote backend, pas a la collecte.
    """
    out = os.path.join(args.out, "jurisite")
    os.makedirs(out, exist_ok=True)
    fetcher = Fetcher(args.delay)
    man = Manifest(os.path.join(out, "manifest.jsonl"))
    codes = [c.strip() for c in args.themes.split(",")] if args.themes else CODES
    print(f"Reprise : {len(man.seen)} page(s) deja au manifeste.", flush=True)

    grand = 0
    for code in codes:
        base = f"{JURISITE}{code}/"
        os.makedirs(os.path.join(out, code), exist_ok=True)
        entry = find_entry(fetcher, base)
        if not entry:
            print(f"  {code:<22} AUCUN SOMMAIRE TROUVE — ignore", file=sys.stderr, flush=True)
            continue
        queue, seen_pages, saved = [entry], set(), 0
        while queue:
            page_name = queue.pop(0)
            if page_name in seen_pages:
                continue
            seen_pages.add(page_name)
            key = f"{code}/{page_name}"
            path = os.path.join(out, code, page_name)
            # Deja collectee : on relit le disque au lieu de resolliciter le site. Une
            # reprise ne doit pas retelecharger 2 200 pages pour redecouvrir les memes liens.
            if key in man.seen and os.path.exists(path):
                body = open(path, encoding="utf-8", errors="replace").read()
                already = True
            else:
                try:
                    body = fetcher.get(base + page_name)
                except RuntimeError:
                    continue   # page absente : un sommaire liste parfois des liens morts
                already = False

            # Les liens internes sont tantot relatifs (« Coc1022.htm »), tantot absolus
            # (« /tunisie/codes/Constitution_2014/const1000p.htm »). Les resoudre contre
            # l'URL courante traite les deux formes ; ne filtrer qu'ensuite sur le dossier
            # du code, sinon des pans entiers de codes sont silencieusement ignores.
            for href in re.findall(r'href="([^"]+)"', body, re.I):
                href = html.unescape(href).split("#")[0].strip()
                if not href or href.lower().startswith(("mailto:", "javascript:", "http")):
                    continue
                target = urllib.parse.urljoin(base + page_name, href)
                if not target.startswith(base):
                    continue                      # hors du dossier de ce code
                name = target[len(base):]
                if "/" in name or not name.lower().endswith((".htm", ".html")):
                    continue
                if name not in seen_pages:
                    queue.append(name)

            if already or key in man.seen:
                continue
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(body)
            # Desechappement obligatoire : « Article&nbsp;5 » doit devenir « Article 5 »
            # avant tout comptage, sans quoi aucun en-tete n'est reconnu.
            text = html.unescape(re.sub(r"<[^>]+>", " ", body)).replace(" ", " ")
            man.add({
                "key": key, "source": "jurisite", "code": code, "page": page_name,
                "url": base + page_name,
                "html_path": os.path.relpath(path, args.out).replace("\\", "/"),
                "bytes": len(body.encode("utf-8")),
                "article_markers": len(ARTICLE_MARKER.findall(text)),
                "is_menu": page_name.lower().startswith("menu"),
                "fetched_at": now(),
            })
            saved += 1
            if args.max and saved >= args.max:
                break
        grand += saved
        print(f"  {code:<22} {len(seen_pages):>4} page(s) vue(s), {saved:>4} enregistree(s)",
              flush=True)

    man.close()
    print(f"\nTermine. {grand} page(s) enregistree(s).")


# ---------------------------------------------------------------- legislation-securite

def html_to_text(fragment):
    """HTML WordPress -> texte brut, en preservant les frontieres de blocs.

    Le saut de ligne sur </p>, <br> et <div> n'est PAS cosmetique :
    LegalArticleChunker ancre ses en-tetes d'article en debut de ligne ((?m)^\\s*).
    Coller les blocs bout a bout ferait echouer TOUT le decoupage par articles.
    """
    s = re.sub(r"(?is)<(script|style).*?</\1>", " ", fragment)
    s = re.sub(r"(?i)<br\s*/?>", "\n", s)
    s = re.sub(r"(?i)</(p|div|li|tr|h[1-6])\s*>", "\n", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = html.unescape(s).replace("\xa0", " ")
    s = re.sub(r"[ \t]+", " ", s)
    return re.sub(r"\n{3,}", "\n\n", s).strip()


def legsec_terms(fetcher, langs):
    """Cartographie id -> nom pour chaque taxonomie, afin que le manifeste soit lisible.

    A INTERROGER LANGUE PAR LANGUE : WPML attribue des identifiants de terme DISTINCTS a
    chaque langue. Une seule passe sans `lang` ne ramene que les termes francais, et les
    fiches arabes ressortent alors avec des identifiants bruts (« 188 ») au lieu de
    « ساري المفعول » — le statut juridique devient illisible, donc inutilisable comme filtre.
    """
    table = {}
    for lang in langs:
        for tax in LEGSEC_TAXONOMIES:
            page = 1
            while True:
                url = f"{LEGSEC_API}/{tax}?per_page=100&lang={lang}&offset={(page-1)*100}"
                try:
                    data = json.loads(fetcher.get(url))
                except (RuntimeError, json.JSONDecodeError):
                    print(f"  taxonomie « {tax} » ({lang}) illisible : ignoree.",
                          file=sys.stderr)
                    break
                if not data:
                    break
                for term in data:
                    table[(tax, term["id"])] = html.unescape(term["name"]).strip()
                if len(data) < 100:
                    break
                page += 1
    return table


def harvest_legsec(args):
    out = os.path.join(args.out, "legislation-securite")
    os.makedirs(out, exist_ok=True)
    # Le crawl-delay annonce prime toujours sur l'option de ligne de commande.
    fetcher = Fetcher(max(args.delay, LEGSEC_MIN_DELAY))
    man = Manifest(os.path.join(out, "manifest.jsonl"))
    langs = [l.strip() for l in (args.themes or "fr,ar").split(",") if l.strip()]
    print(f"Reprise : {len(man.seen)} fiche(s) deja au manifeste. Langues : {langs}")
    print(f"Delai applique : {fetcher.delay:.0f}s (robots.txt Crawl-delay: 10)\n")

    terms = legsec_terms(fetcher, langs)
    with open(os.path.join(out, "taxonomies.json"), "w", encoding="utf-8") as fh:
        json.dump({f"{t}:{i}": n for (t, i), n in terms.items()}, fh,
                  ensure_ascii=False, indent=2)

    total_new = 0
    for lang in langs:
        os.makedirs(os.path.join(out, lang), exist_ok=True)
        offset, seen_lang, placeholders = 0, 0, 0
        while True:
            # `offset` plutot que `page` : robots.txt interdit le motif « ?page= », et
            # l'offset evite toute ambiguite sur ce point.
            url = (f"{LEGSEC_API}/latest-laws?per_page=100&offset={offset}"
                   f"&lang={lang}&orderby=id&order=asc")
            try:
                batch = json.loads(fetcher.get(url))
            except RuntimeError as e:
                print(f"  [{lang}] offset={offset} ECHEC : {e}", file=sys.stderr)
                break
            except json.JSONDecodeError:
                break
            if not batch:
                break

            for rec in batch:
                seen_lang += 1
                key = f"{lang}:{rec['id']}"
                if key in man.seen:
                    continue
                path = os.path.join(out, lang, f"{rec['id']}.json")
                with open(path, "w", encoding="utf-8") as fh:
                    json.dump(rec, fh, ensure_ascii=False)

                texte = html_to_text(rec.get("content", {}).get("rendered", ""))
                titre = html_to_text(rec.get("title", {}).get("rendered", ""))
                # Fiche inexploitable : soit vide, soit un simple avis d'indisponibilite.
                # Le cas vide doit etre couvert explicitement — aucune phrase temoin n'y
                # figure, et sans ce test il passerait pour du contenu valide.
                creux = (not texte) or (len(texte) < 400
                                        and any(p in texte for p in LEGSEC_PLACEHOLDERS))
                placeholders += creux

                def labels(tax):
                    return [terms.get((tax, i), str(i)) for i in rec.get(tax, [])]

                man.add({
                    "key": key, "source": "legislation-securite", "lang": lang,
                    "wp_id": rec["id"], "slug": rec.get("slug", ""),
                    "title": titre, "link": rec.get("link", ""),
                    "posted": rec.get("date", "")[:10],
                    "modified": rec.get("modified", "")[:10],
                    "text_type": labels("text-type-categories"),
                    "institution": labels("institution-categories"),
                    "status": labels("status-categories"),
                    "thematic": labels("thematic-folders-categories"),
                    "chars": len(texte),
                    "placeholder": creux,
                    "json_path": os.path.relpath(path, args.out).replace("\\", "/"),
                    "fetched_at": now(),
                })
                total_new += 1

            print(f"  [{lang}] {seen_lang} fiche(s) parcourue(s), "
                  f"{placeholders} sans texte", flush=True)
            offset += len(batch)
            if args.max and seen_lang >= args.max:
                break

    man.close()
    print(f"\nTermine. {total_new} nouvelle(s) fiche(s).")


# ---------------------------------------------------------------- jort.tn

JORT = "https://jort.tn"
JORT_SITEMAP = JORT + "/sitemap-journal-officiel.xml"
# Les PDF sont servis par un hote distinct du site de consultation.
JORT_LAKE = "https://lake.jort.tn/journal-officiel/{lang}/{year}/{issue}.pdf"


def harvest_jort(args):
    """Numeros du JORT depuis le miroir jort.tn.

    CADRE D'UTILISATION — le robots.txt des deux hotes porte :
        Content-Signal: search=yes, ai-train=no, use=reference
    `ai-train=no` interdit l'entrainement ou le reglage fin d'un modele sur ce fonds.
    `ai-input` — le signal qui vise nommement la generation augmentee par recuperation —
    n'est PAS renseigne : la regle (c) du fichier le laisse donc ni accorde ni refuse.
    La presente collecte alimente un index vectoriel destine a la citation, pas un
    entrainement. NE PAS s'en servir pour entrainer quoi que ce soit.

    Le poids total (~15 Go sur 12 738 fichiers) impose de borner la collecte : voir
    --years. Toujours verifier l'espace disque avant une passe complete.
    """
    out = os.path.join(args.out, "jort")
    os.makedirs(os.path.join(out, "pdf"), exist_ok=True)
    fetcher = Fetcher(args.delay)
    man = Manifest(os.path.join(out, "manifest.jsonl"))
    langs = [l.strip() for l in (args.themes or "fr,ar").split(",") if l.strip()]

    debut, fin = 0, 9999
    if args.years:
        bornes = args.years.split("-")
        debut = int(bornes[0])
        fin = int(bornes[-1]) if len(bornes) > 1 else debut

    print(f"Reprise : {len(man.seen)} numero(s) deja au manifeste.")
    print(f"Annees {debut}-{fin}, langues {langs}\n")

    plan = []
    for url in re.findall(r"<loc>([^<]+)</loc>", fetcher.get(JORT_SITEMAP)):
        m = re.search(r"/journal-officiel/(\w+)/(\d{4})/([^/]+)/?$", url)
        if not m:
            continue
        lang, annee, numero = m.group(1), int(m.group(2)), m.group(3)
        if lang in langs and debut <= annee <= fin:
            plan.append((lang, annee, numero, url))
    plan.sort(key=lambda x: (-x[1], x[0], x[2]))          # les plus recents d'abord
    todo = [p for p in plan if f"{p[0]}:{p[1]}:{p[2]}" not in man.seen]
    if args.max:
        todo = todo[:args.max]
    print(f"{len(plan)} numero(s) dans le perimetre ; {len(todo)} a telecharger "
          f"(~{len(todo) * 1.2 / 1024:.1f} Go estimes).\n")
    if args.dry_run:
        man.close()
        return

    stats = {"ok": 0, "sans_texte": 0, "echec": 0}
    octets = 0
    for i, (lang, annee, numero, page_url) in enumerate(todo, 1):
        pdf_url = JORT_LAKE.format(lang=lang, year=annee, issue=numero)
        dossier = os.path.join(out, "pdf", lang, str(annee))
        os.makedirs(dossier, exist_ok=True)
        chemin = os.path.join(dossier, f"{numero}.pdf")
        # Filet large : un document en echec — reseau, PDF illisible, disque plein —
        # ne doit jamais interrompre une collecte de plusieurs milliers de fichiers.
        try:
            blob = fetcher.get(pdf_url, binary=True)
            with open(chemin, "wb") as fh:
                fh.write(blob)
            # Ces numeros sont OCRises : la couche texte existe mais sa QUALITE varie
            # fortement selon l'epoque. On enregistre de quoi la mesurer plus tard.
            info = inspect_pdf(chemin, "")
            empreinte = sha256(chemin)
        except Exception as e:
            print(f"[{i}/{len(todo)}] {lang}/{annee}/{numero} ECHEC {type(e).__name__}: {e}",
                  file=sys.stderr, flush=True)
            stats["echec"] += 1
            continue
        octets += len(blob)
        stats["ok" if info["text_layer"] else "sans_texte"] += 1
        man.add({
            "key": f"{lang}:{annee}:{numero}", "source": "jort", "lang": lang,
            "year": annee, "issue": numero, "page_url": page_url, "pdf_url": pdf_url,
            "pdf_path": os.path.relpath(chemin, args.out).replace("\\", "/"),
            "bytes": len(blob), "sha256": empreinte,
            "pages": info["pages"], "chars": info["chars"],
            "arabic_chars": info["arabic_chars"], "text_layer": info["text_layer"],
            "fetched_at": now(),
        })
        if i % 50 == 0 or i == len(todo):
            print(f"[{i}/{len(todo)}] {stats['ok']} ok · {stats['sans_texte']} sans couche "
                  f"texte · {stats['echec']} echecs · {octets/1024**3:.2f} Go", flush=True)

    man.close()
    print(f"\nTermine. {stats} — {octets/1024**3:.2f} Go")


# ---------------------------------------------------------------- africa-laws.org

AFRICA = "https://www.africa-laws.org"


def harvest_africa(args):
    """PDF officiels (edites par l'Imprimerie Officielle) mis en ligne par africa-laws.org.

    Complement indispensable a Jurisite : le diff sur le COC a montre 84 articles
    presents dans le texte officiel et absents de la consolidation Jurisite. Contient
    aussi des versions ARABES et le Code des douanes, absent de Jurisite.
    """
    out = os.path.join(args.out, "africa-laws")
    os.makedirs(out, exist_ok=True)
    fetcher = Fetcher(args.delay)
    man = Manifest(os.path.join(out, "manifest.jsonl"))
    print(f"Reprise : {len(man.seen)} PDF deja au manifeste.")

    def liens(url):
        return [html.unescape(h) for h in re.findall(r'href=["\']([^"\']+)["\']',
                                                     fetcher.get(url))]

    dossiers = [h for h in liens(AFRICA + "/Tunisia/")
                if h.startswith("/Tunisia/") and h.endswith("/")]
    cibles = []
    for d in dossiers:
        for h in liens(AFRICA + urllib.parse.quote(urllib.parse.unquote(d), safe="/")):
            if h.lower().endswith(".pdf"):
                cibles.append(h if h.startswith("/") else d + h)
    cibles = sorted(set(cibles))
    print(f"{len(dossiers)} dossier(s), {len(cibles)} PDF reperes.\n")

    stats = {"ok": 0, "sans_texte": 0, "echec": 0}
    for i, rel in enumerate(cibles, 1):
        nom = urllib.parse.unquote(os.path.basename(rel))
        matiere = urllib.parse.unquote(rel.split("/")[2]) if len(rel.split("/")) > 3 else ""
        key = f"{matiere}/{nom}"
        if key in man.seen:
            continue
        url = AFRICA + urllib.parse.quote(urllib.parse.unquote(rel), safe="/")
        sous = os.path.join(out, re.sub(r"[^\w -]", "_", matiere))
        os.makedirs(sous, exist_ok=True)
        chemin = os.path.join(sous, nom_court(nom))
        # Filet large : chemin trop long, PDF illisible, disque plein — rien de tout cela
        # ne doit interrompre la collecte.
        try:
            blob = fetcher.get(url, binary=True)
            with open(chemin, "wb") as fh:
                fh.write(blob)
            info = inspect_pdf(chemin, "")
            empreinte = sha256(chemin)
        except Exception as e:
            print(f"[{i}/{len(cibles)}] ECHEC {nom[:55]} ({type(e).__name__}: {e})",
                  file=sys.stderr, flush=True)
            stats["echec"] += 1
            continue
        stats["ok" if info["text_layer"] else "sans_texte"] += 1
        # Un PDF arabe NUMERISE rend 0 caractere : le ratio ne dit alors rien. On se
        # rabat sur le titre, qui reste en arabe meme quand la page est une image.
        arabe = (info["arabic_chars"] > max(1, info["chars"]) * 0.3
                 or sum(1 for c in nom if "؀" <= c <= "ۿ") > 5)
        man.add({
            "key": key, "source": "africa-laws", "matiere": matiere, "titre": nom,
            "url": url, "pdf_path": os.path.relpath(chemin, args.out).replace("\\", "/"),
            "bytes": len(blob), "sha256": empreinte,
            "pages": info["pages"], "chars": info["chars"],
            "arabic_chars": info["arabic_chars"], "text_layer": info["text_layer"],
            "lang": "ar" if arabe else "fr",
            "fetched_at": now(),
        })
        print(f"[{i}/{len(cibles)}] {nom[:62]:<64} "
              f"{info['pages']:>4}p {'OK' if info['text_layer'] else 'SCAN'}", flush=True)

    man.close()
    print(f"\nTermine. {stats}")


# ---------------------------------------------------------------- cli

def main():
    ap = argparse.ArgumentParser(
        description="Collecte hors ligne du corpus juridique tunisien.")
    ap.add_argument("source", choices=["cassation", "jurisite", "legislation-securite",
                                       "jort", "africa-laws"])
    ap.add_argument("--out", required=True,
                    help="repertoire de transit — A TENIR HORS DU DEPOT (~2 Go)")
    ap.add_argument("--delay", type=float, default=1.5,
                    help="secondes entre deux requetes (defaut 1.5 ; ne pas descendre sous 1 ; "
                         "legislation-securite force 10 s, conformement a son robots.txt)")
    ap.add_argument("--themes", default="",
                    help="cassation : matieres (TA,TB...) ; jurisite : codes (coc,cp...) ; "
                         "legislation-securite : langues (fr,ar)")
    ap.add_argument("--max", type=int, default=0, help="plafond de documents (0 = illimite)")
    ap.add_argument("--years", default="",
                    help="jort : borne les annees, p.ex. 2000-2026 ou 2024")
    ap.add_argument("--dry-run", action="store_true",
                    help="cassation / jort : recense sans rien telecharger")
    args = ap.parse_args()

    if args.delay < 1.0:
        sys.exit("--delay < 1 s : refus. Ce sont des services publics.")
    os.makedirs(args.out, exist_ok=True)

    started = time.time()
    {"cassation": harvest_cassation,
     "jurisite": harvest_jurisite,
     "legislation-securite": harvest_legsec,
     "jort": harvest_jort,
     "africa-laws": harvest_africa}[args.source](args)
    print(f"Duree : {(time.time() - started) / 60:.1f} min")


if __name__ == "__main__":
    main()
