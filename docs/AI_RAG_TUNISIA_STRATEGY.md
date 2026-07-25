# Tunisian Legal RAG System — Strategy & Architecture

## Overview
This document outlines the data acquisition, ingestion, chunking, and retrieval strategy for ForsaLaw's AI RAG system, specifically tailored to the nuances of the Tunisian legal system.

---

## The 3-Tier Legal Corpus Architecture

### Tier 1: Statutory Law (JORT Pipeline)
- **Source:** *Journal Officiel de la République Tunisienne* (JORT / الرائد الرسمي للجمهورية التونسية) via `iort.gov.tn` and `legislation.tn`.
- **Content:** Foundational Codes (*Code des Obligations et des Contrats*, *Code de Procédure Civile*, *Code Pénal*, *Code de Commerce*, etc.), Presidential Decrees (*مرسوم*), Government Orders (*أوامر حكومية*), and Ministerial Decrees (*قرارات وزارية*).
- **Ingestion:** Automated bi-weekly scraper (Tuesdays & Fridays) downloading new JORT issues.
- **Versioning Strategy:** Each chunk in `pgvector` contains `code_name`, `article_reference`, `effective_date`, and `status` (`ACTIVE` / `SUPERSEDED`). When a new law amends an existing article (*يلغى وتعوض أحكام الفصل...*), the old chunk is marked `SUPERSEDED` and the new chunk is inserted as `ACTIVE`.

### Tier 2: Jurisprudence (CEJJ & Court of Cassation Rulings)
- **Source:** *Centre d'Études Juridiques et Judiciaires* (CEJJ / مركز الدراسات القانونية والقضائية - `cejj.tn`) annual volumes (*نشريات محكمة التعقيب*) and legal compendiums (*المجاميع الفقهية*).
- **Content:** Rulings of the Court of Cassation (*قرارات محكمة التعقيب*), Appellate judgments, and scholarly legal commentaries.
- **Ingestion:** Multilingual OCR pipeline (Arabic/French) extracting structured text, legal principles, and article references from scanned PDF bulletins.

### Tier 3: Private Law Firm Vaults (User-Uploaded Data)
- **Source:** Law firms' internal case archives, past written briefs (*مذكرات*), contracts, and precedent collections.
- **Content:** Firm-specific confidential documents stored in S3/MinIO.
- **Retrieval:** Combined RAG query searching both the **Global Legal Library** (Tiers 1 & 2) and the **Firm's Private Library** (Tier 3) simultaneously with strict tenant isolation.

---

## Value-Add Feature: JORT Legal Watch Digest
Whenever a new JORT issue is ingested, the system automatically generates an AI summary digest notifying subscribed lawyers of new legislation, amendments, or administrative orders impacting their domain.
