#!/usr/bin/env python3
#
# Conversion du corpus BRUT en Markdown pret pour l'ingestion RAG.
#
#   python scripts/convert-corpus.py all        --raw ../corpus-staging/raw --out ../corpus-staging/md
#   python scripts/convert-corpus.py cassation  --raw ../corpus-staging/raw --out ../corpus-staging/md
#
# COUCHE DERIVEE, JAMAIS SOURCE DE VERITE :
#   `raw/` est immuable et empreinte ; `md/` se regenere a volonte. Ne jamais editer un
#   fichier de `md/` a la main — il sera ecrase. Toute correction se fait ici.
#
# CONTRAT AVEC LegalArticleChunker — chaque regle vient d'un defaut MESURE :
#
#   1. TOUT EN-TETE D'ARTICLE COMMENCE SA PROPRE LIGNE.
#      Le chunker ancre ses marqueurs en debut de ligne ((?m)^\s*). Sans saut de ligne
#      sur les frontieres de blocs HTML, le COC perd 259 de ses 1 330 en-tetes (81 %
#      seulement en debut de ligne) et le chunker se rabat SILENCIEUSEMENT sur un
#      decoupage par fenetres. Mesure faite : avec sauts de bloc, 100 %.
#      Cote PDF, l'extraction place deja 100 % des en-tetes en debut de ligne — aucune
#      heuristique de promotion n'est appliquee, elle ne ferait qu'introduire des faux
#      positifs sur « conformement a l'article 5 ».
#
#   2. LES METADONNEES VIENNENT DU MANIFESTE, JAMAIS DU CORPS DU DOCUMENT.
#      Deux arrets de cassation rendent des chiffres CORROMPUS a l'extraction
#      (« 2013 » lu « 3182 ») : ce n'est pas une inversion, c'est un defaut d'encodage
#      de police, donc irrecuperable. Numero et date proviennent du tableau HTML.
#
#   3. LES FICHES SANS CONTENU SONT EXCLUES.
#      1 545 fiches DCAF sont vides ou ne portent qu'un avis d'indisponibilite.
#
#   4. LE STATUT JURIDIQUE VOYAGE AVEC LE TEXTE.
#      ~15 % des fiches DCAF ne sont plus en vigueur. Repondre a partir d'un texte
#      abroge sans le dire est pire que ne pas repondre.
#
#   5. LES DOCUMENTS MIS EN QUARANTAINE NE SONT PAS CONVERTIS.
#
# DEPENDANCE : pypdf

import argparse
import json
import os
import re
import sys
import unicodedata
from datetime import datetime, timezone

try:
    from pypdf import PdfReader
except ImportError:
    sys.exit("pypdf manquant : pip install pypdf")

# Meme famille de graphies que LegalArticleChunker cote backend. Sert ici a COMPTER les
# articles pour le controle qualite, pas a decouper.
ARTICLE = re.compile(
    r"(?m)^\s*(?:(?:الفـ*صـ*ل|المـ*ادة)\s*[:.\-]?\s*[0-9\u0660-\u0669]+"
    r"|(?:Articles?|ARTICLES?|Arts?|ARTS?)\.?\s*[:.\-]?\s*"
    r"(?:premier|[0-9\u0660-\u0669]+))")

# Arrets dont l'integrite est douteuse — voir docs/CORPUS_MANIFEST.md.
QUARANTAINE_CASSATION = {"6187.13", "59045.18", "31643.18", "34721.22"}


def now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def yaml_escape(v):
    if v is None:
        return '""'
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float)):
        return str(v)
    if isinstance(v, list):
        return "[" + ", ".join(yaml_escape(x) for x in v) + "]"
    return '"' + str(v).replace("\\", "\\\\").replace('"', '\\"').replace("\n", " ") + '"'


def nom_sur(nom, limite=80):
    """Nom de fichier sur : Windows plafonne un chemin complet a 260 caracteres.

    Les intitules officiels arabes depassent la limite, et l'ecriture echoue alors avec
    FileNotFoundError. L'intitule integral reste dans le frontmatter, champ `title`.
    """
    base = re.sub(r"[^\w.() -]", "_", nom).strip() or "document"
    if len(base) <= limite:
        return base
    import hashlib
    return f"{base[:limite]}_{hashlib.sha256(nom.encode('utf-8')).hexdigest()[:8]}"


def ecrire_md(chemin, meta, corps):
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    with open(chemin, "w", encoding="utf-8") as fh:
        fh.write("---\n")
        for k, v in meta.items():
            fh.write(f"{k}: {yaml_escape(v)}\n")
        fh.write("---\n\n")
        fh.write(corps.strip() + "\n")


def nettoyer(texte):
    """Normalisation commune, SANS toucher aux frontieres de lignes.

    Les sauts de ligne portent le contrat avec le chunker : on ne les compacte jamais
    au point de recoller deux blocs.

    NFKC est INDISPENSABLE : certains PDF arabes encodent les lettres en formes de
    presentation Unicode (U+FB50–FDFF, U+FE70–FEFF) au lieu de l'arabe standard
    (U+0600–06FF). Sans normalisation, « الفصل » ne ressemble a rien de reconnaissable et
    tous les en-tetes du document disparaissent. Mesure sur les 6 fichiers concernes :
    1 474 en-tetes d'article recuperes, dont la mjalla des societes commerciales (0 -> 459)
    et la MRDC/CPCC arabe (0 -> 461). Cote JORT, 40 % des numeros arabes sont concernes.

    NFKC ne touche PAS au kaf persan U+06A9 parfois produit par l'OCR a la place du kaf
    arabe U+0643 : deux lettres distinctes, pas des variantes de glyphe.

    Le filet de securite reel est cote backend — LegalArticleChunker.normaliser() applique
    aussi NFKC, ce qui couvre les sources futures et le texte OCRise. La normalisation ici
    sert a garder `md/` propre, pas a proteger le pipeline.
    """
    texte = unicodedata.normalize("NFKC", texte)
    t = texte.replace("\xa0", " ").replace("\r\n", "\n").replace("\r", "\n")
    t = re.sub(r"[ \t]+", " ", t)
    t = re.sub(r" *\n *", "\n", t)
    return re.sub(r"\n{3,}", "\n\n", t).strip()


def html_vers_texte(fragment):
    """HTML -> texte en PRESERVANT les frontieres de blocs (regle 1)."""
    s = re.sub(r"(?is)<(script|style).*?</\1>", " ", fragment)
    s = re.sub(r"(?i)<br\s*/?>", "\n", s)
    s = re.sub(r"(?i)</(p|div|li|tr|td|h[1-6]|blockquote)\s*>", "\n", s)
    s = re.sub(r"<[^>]+>", "", s)
    import html as _h
    return nettoyer(_h.unescape(s))


def pdf_vers_texte(chemin):
    reader = PdfReader(chemin)
    return nettoyer("".join((p.extract_text() or "") for p in reader.pages))


def lire_manifeste(raw, source):
    p = os.path.join(raw, source, "manifest.jsonl")
    if not os.path.exists(p):
        return []
    out = []
    with open(p, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                try:
                    out.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
    return out


class Sortie:
    """Manifeste de conversion + compteurs.

    UN MANIFESTE PAR SOURCE, jamais un fichier partage : deux conversions lancees en
    parallele sur un meme JSONL entrelacent leurs ecritures et le corrompent (constate).
    Le mode « w » plutot que « a » rend en outre chaque passe idempotente — relancer une
    conversion ne duplique plus les lignes.
    """

    def __init__(self, out, source):
        os.makedirs(out, exist_ok=True)
        self.fh = open(os.path.join(out, f"manifest-{source}.jsonl"), "w", encoding="utf-8")
        self.n = 0
        self.ignores = {}

    def ajouter(self, rec):
        self.fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
        self.fh.flush()
        self.n += 1

    def ignorer(self, motif):
        self.ignores[motif] = self.ignores.get(motif, 0) + 1

    def fermer(self):
        self.fh.close()


# ---------------------------------------------------------------- sources

def conv_cassation(raw, out, sortie, limite):
    for r in lire_manifeste(raw, "cassation")[:limite or None]:
        if r["doc_number"] in QUARANTAINE_CASSATION:
            sortie.ignorer("quarantaine")
            continue
        if not r.get("text_layer"):
            sortie.ignorer("sans couche texte")
            continue
        src = os.path.join(raw, r["pdf_path"])
        try:
            corps = pdf_vers_texte(src)
        except Exception:
            sortie.ignorer("pdf illisible")
            continue
        if len(corps) < 200:
            sortie.ignorer("texte trop court")
            continue
        cible = os.path.join(out, "cassation", f"{r['doc_number']}.md")
        # Metadonnees issues du TABLEAU HTML (regle 2) : jamais relues dans le PDF.
        ecrire_md(cible, {
            "source": "cassation", "tier": 2, "id": r["doc_number"],
            "case_number": r["case_number"], "date": r["date"], "lang": "ar",
            "juridiction": "Cour de cassation", "matiere": r["theme_label"],
            "headnote": r["headnote"], "url": r["pdf_url"], "sha256_raw": r["sha256"],
            "pages": r["pages"], "digit_check": r["digit_check"],
            "converted_at": now(),
        }, corps)
        sortie.ajouter({"md": os.path.relpath(cible, out).replace("\\", "/"),
                        "source": "cassation", "id": r["doc_number"],
                        "chars": len(corps), "articles": len(ARTICLE.findall(corps))})


def conv_jurisite(raw, out, sortie, limite):
    for r in lire_manifeste(raw, "jurisite")[:limite or None]:
        if r.get("is_menu"):
            sortie.ignorer("sommaire")
            continue
        src = os.path.join(raw, r["html_path"])
        if not os.path.exists(src):
            sortie.ignorer("fichier absent")
            continue
        brut = open(src, encoding="utf-8", errors="replace").read()
        m = re.search(r'#BeginEditable "texte" -->(.*?)<!-- #EndEditable', brut, re.S)
        corps = html_vers_texte(m.group(1) if m else brut)
        if len(corps) < 200:
            sortie.ignorer("texte trop court")
            continue
        cible = os.path.join(out, "jurisite", r["code"],
                             os.path.splitext(r["page"])[0] + ".md")
        ecrire_md(cible, {
            "source": "jurisite", "tier": 1, "code": r["code"], "page": r["page"],
            "lang": "fr", "url": r["url"], "consolidation": "non officielle",
            "converted_at": now(),
        }, corps)
        sortie.ajouter({"md": os.path.relpath(cible, out).replace("\\", "/"),
                        "source": "jurisite", "id": f"{r['code']}/{r['page']}",
                        "chars": len(corps), "articles": len(ARTICLE.findall(corps))})


def conv_legsec(raw, out, sortie, limite):
    for r in lire_manifeste(raw, "legislation-securite")[:limite or None]:
        if r.get("placeholder"):
            sortie.ignorer("fiche sans texte")          # regle 3
            continue
        src = os.path.join(raw, r["json_path"])
        if not os.path.exists(src):
            sortie.ignorer("fichier absent")
            continue
        d = json.load(open(src, encoding="utf-8"))
        corps = html_vers_texte(d.get("content", {}).get("rendered", ""))
        if len(corps) < 120:
            sortie.ignorer("texte trop court")
            continue
        cible = os.path.join(out, "legislation-securite", r["lang"], f"{r['wp_id']}.md")
        ecrire_md(cible, {
            "source": "legislation-securite", "tier": 1, "id": str(r["wp_id"]),
            "title": r["title"], "lang": r["lang"],
            # regle 4 : le statut voyage avec le texte.
            "statut": r["status"], "text_type": r["text_type"],
            "institution": r["institution"], "url": r["link"],
            "posted": r["posted"], "converted_at": now(),
        }, corps)
        sortie.ajouter({"md": os.path.relpath(cible, out).replace("\\", "/"),
                        "source": "legislation-securite", "id": f"{r['lang']}:{r['wp_id']}",
                        "chars": len(corps), "articles": len(ARTICLE.findall(corps)),
                        "statut": r["status"]})


def conv_pdf_simple(raw, out, sortie, limite, source, meta_fn, cible_fn):
    """Tronc commun des sources PDF (africa-laws, jort, bct)."""
    for r in lire_manifeste(raw, source)[:limite or None]:
        if not r.get("text_layer"):
            sortie.ignorer("sans couche texte")
            continue
        src = os.path.join(raw, r["pdf_path"])
        if not os.path.exists(src):
            sortie.ignorer("fichier absent")
            continue
        try:
            corps = pdf_vers_texte(src)
        except Exception:
            sortie.ignorer("pdf illisible")
            continue
        if len(corps) < 200:
            sortie.ignorer("texte trop court")
            continue
        try:
            cible = cible_fn(out, r)
            meta = meta_fn(r)
            meta["converted_at"] = now()
            ecrire_md(cible, meta, corps)
        except Exception as e:
            print(f"  ECHEC ecriture {r['key'][:60]} ({type(e).__name__})", file=sys.stderr)
            sortie.ignorer("ecriture impossible")
            continue
        sortie.ajouter({"md": os.path.relpath(cible, out).replace("\\", "/"),
                        "source": source, "id": r["key"],
                        "chars": len(corps), "articles": len(ARTICLE.findall(corps))})


def conv_africa(raw, out, sortie, limite):
    conv_pdf_simple(
        raw, out, sortie, limite, "africa-laws",
        lambda r: {"source": "africa-laws", "tier": 1, "id": r["key"],
                   "title": r["titre"], "matiere": r["matiere"], "lang": r["lang"],
                   "url": r["url"], "sha256_raw": r["sha256"], "pages": r["pages"],
                   "consolidation": "officielle (Imprimerie Officielle)"},
        lambda o, r: os.path.join(o, "africa-laws",
                                  nom_sur(r["matiere"], 40),
                                  nom_sur(os.path.splitext(os.path.basename(r["pdf_path"]))[0]) + ".md"))


def conv_jort(raw, out, sortie, limite):
    conv_pdf_simple(
        raw, out, sortie, limite, "jort",
        lambda r: {"source": "jort", "tier": 1, "id": r["key"],
                   "journal": "Journal Officiel de la Republique Tunisienne",
                   "year": r["year"], "issue": r["issue"], "lang": r["lang"],
                   "url": r["page_url"], "sha256_raw": r["sha256"], "pages": r["pages"],
                   "usage": "index vectoriel et citation uniquement (ai-train=no)"},
        lambda o, r: os.path.join(o, "jort", r["lang"], str(r["year"]),
                                  f"{r['issue']}.md"))


def conv_bct(raw, out, sortie, limite):
    conv_pdf_simple(
        raw, out, sortie, limite, "bct",
        lambda r: {"source": "bct", "tier": 1, "id": r["key"],
                   "title": r["title"], "doc_type": r["doc_type"], "year": r["year"],
                   "number": r["number"], "lang": r["lang"], "url": r["url"],
                   "sha256_raw": r["sha256"], "pages": r["pages"],
                   "institution": "Banque Centrale de Tunisie"},
        lambda o, r: os.path.join(o, "bct",
                                  nom_sur(os.path.splitext(os.path.basename(r["pdf_path"]))[0]) + ".md"))


SOURCES = {"cassation": conv_cassation, "jurisite": conv_jurisite,
           "legislation-securite": conv_legsec, "africa-laws": conv_africa,
           "jort": conv_jort, "bct": conv_bct}


def main():
    ap = argparse.ArgumentParser(description="raw/ -> md/ pour l'ingestion RAG.")
    ap.add_argument("source", choices=list(SOURCES) + ["all"])
    ap.add_argument("--raw", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--limit", type=int, default=0, help="plafond par source (0 = illimite)")
    args = ap.parse_args()

    cibles = list(SOURCES) if args.source == "all" else [args.source]
    for nom in cibles:
        sortie = Sortie(args.out, nom)
        print(f"\n=== {nom} ===", flush=True)
        SOURCES[nom](args.raw, args.out, sortie, args.limit)
        print(f"  {sortie.n} fichier(s) Markdown ecrit(s)")
        if sortie.ignores:
            for motif, n in sorted(sortie.ignores.items(), key=lambda x: -x[1]):
                print(f"  ignore — {motif}: {n}")
        sortie.fermer()


if __name__ == "__main__":
    main()
