# ROTA — Proje Planı

> **Bu doküman nedir?**
> Rota projesinin tek-doğru-referans planıdır. Tüm geliştirme bu dosyaya bağlı olarak yapılacaktır.
>
> **Bu dokümanı kim, nasıl kullanacak?**
> - **İnsan geliştirici (kurucu):** Kararların gerekçesini ve genel mimari hikayeyi okumak, ilerlemeyi izlemek için kullanır.
> - **Claude Code (AI geliştirici):** Bu dokümanı baştan sona okur, sırayla uygular. Önce **Faz 0 (Yerel Kurulum)**, sonra **Faz 1**, ... şeklinde ilerler. Her fazın **kabul kriterleri** karşılanmadan bir sonraki faza geçilmez.
>
> **Versiyonlama notu:** Major.minor versiyonları sabitlenmiştir. Patch versiyonu için kurulum zamanında en güncel kararlı sürüm tercih edilir. "Latest" gibi belirsiz ifadeler yoktur.

---

## İçindekiler

1. [Proje Vizyonu](#1-proje-vizyonu)
2. [V1 Kapsamı ve Kapsam Dışı](#2-v1-kapsamı-ve-kapsam-dışı)
3. [Fiyatlandırma Katmanları](#3-fiyatlandırma-katmanları)
4. [Tam Teknoloji Yığını](#4-tam-teknoloji-yığını)
5. [Faz 0 — Yerel Geliştirme Ortamı Kurulumu](#5-faz-0--yerel-geliştirme-ortamı-kurulumu)
6. [Monorepo Yapısı](#6-monorepo-yapısı)
7. [Mimari Genel Bakış](#7-mimari-genel-bakış)
8. [Multi-Tenancy ve Güvenlik Mimarisi](#8-multi-tenancy-ve-güvenlik-mimarisi)
9. [Veritabanı Tasarımı](#9-veritabanı-tasarımı)
10. [Backend Modülleri (Spring Modulith)](#10-backend-modülleri-spring-modulith)
11. [Frontend Mimarisi](#11-frontend-mimarisi)
12. [Desktop Uygulama Mimarisi](#12-desktop-uygulama-mimarisi)
13. [Anahtar Özelliklerin Detaylı Tasarımı](#13-anahtar-özelliklerin-detaylı-tasarımı)
14. [CI/CD, Observability ve Operasyonel Altyapı](#14-cicd-observability-ve-operasyonel-altyapı)
15. [Test Stratejisi](#15-test-stratejisi)
16. [Geliştirme Fazları (Yol Haritası)](#16-geliştirme-fazları-yol-haritası)
17. [Açık Riskler ve Ertelenmiş Kararlar](#17-açık-riskler-ve-ertelenmiş-kararlar)
18. [Sözlük](#18-sözlük)

---

## 1. Proje Vizyonu

**Rota**, API sahiplerinin **interaktif** API dokümantasyonu oluşturup yayınladığı, API tüketicilerinin de bu dokümantasyon üzerinden API'yi keşfedip test edebildiği bir SaaS platformdur.

**Hedef kullanıcı tipleri:**
- **API Sahibi (publisher):** Kendi API'sini Rota'da dokümante eden geliştirici/ekip. Endpoint ekler, akış diyagramı çizer, izinleri yönetir, yayınlar.
- **API Tüketicisi (consumer):** Yayınlanmış bir dokümana erişen kişi. Endpoint'leri okur, akışı görselleştirir, izin verilmişse "Try It" ile istek atar.

**Asıl farklılaştırıcı:** Endpoint'ler arası **interaktif akış diyagramları** (state machine / flow chart tipi). Pazarda sequence diagram veya statik Mermaid çıktısı sunan ürünler var, ama Rota'nın diyagramında **node'lar tıklanabilir** (endpoint dokümanına atlar), **edge'lere koşul yazılabilir** ("response.status == 'pending' ise loop"), **döngü ve dallanma** desteklenir.

**Deployment topolojisi:** Aynı codebase iki modda çalışır:
- **SaaS modu:** Bizim sunucumuzda multi-tenant. Tüketici dokümanlara `https://{tenant}.rotadoc.com` veya custom domain üzerinden erişir.
- **On-prem modu:** Büyük müşterilerin kendi sunucularında tek-tenant. Docker Compose veya Kubernetes Helm chart ile dağıtılır.

---

## 2. V1 Kapsamı ve Kapsam Dışı

### V1'de olacaklar

1. Multi-tenant SaaS + on-prem deploy edilebilir mimari (baştan).
2. Kullanıcı yönetimi: Organizasyon, roller (Owner/Admin/Editor/Viewer), consumer group'lar.
3. **REST API** dokümantasyonu.
4. Endpoint ekleme yolları: manuel, cURL yapıştırma, **OpenAPI 3.x import**, **Postman Collection v2.1 import**.
5. Tüm HTTP metodları (GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS), path variable yönetimi, header/query/body parametreleri, JSON/form/multipart body, auth tipleri (Bearer/API Key/Basic/OAuth2).
6. Kategori sistemi.
7. **State machine / flow chart** tipinde interaktif akış diyagramı.
8. Endpoint başına Markdown açıklama.
9. **Mock server** (endpoint başına gerçek/mock toggle).
10. **Environment yönetimi:** Test/Staging/Prod base URL'leri. Prod'a istek uyarısı.
11. **Try It** — üç modda: server-proxy (varsayılan) / browser-direct / desktop-only.
12. **Otomatik code samples:** cURL, Python (requests), JavaScript (fetch), Java (HttpClient), Go (net/http), PHP, C#.
13. **Versioning** + **otomatik changelog** (diff özeti).
14. **Yük testi** (Pro+, doğrulanmış domain).
15. **İstek geçmişi, favoriler, replay.**
16. **Custom branding** (logo, renk) — Pro+.
17. **Custom domain (CNAME)** — Pro+, Let's Encrypt otomasyonu.
18. **Audit log** (tüm CUD işlemleri için, hash-chain immutability).
19. **Activity log** (Try It, login, vs.).
20. Hassas alanların **at-rest encryption** (AES-256-GCM, envelope pattern).
21. **Web app + Desktop app** (Tauri).
22. UI dilleri: **Türkçe + İngilizce.**
23. **Payment integration:** Paddle veya Lemon Squeezy (Merchant of Record).
24. **Email:** Resend.

### V1'de olmayacaklar (gelecekte)

- GraphQL / WebSocket / gRPC desteği (veri modeli bunu engellemeyecek)
- PDF/Word'den AI ile otomatik endpoint çıkarma
- Doğal dilden endpoint açıklaması yazma / AI assist
- `llms.txt` ve MCP server export'u
- Zero-knowledge Try mode
- Gelişmiş analytics (heatmap, en çok aranan endpoint vs.)
- Cross-region replikasyon
- SAML SSO (Enterprise)

---

## 3. Fiyatlandırma Katmanları

| Özellik | Free | Pro | Business | Enterprise |
|---|---|---|---|---|
| Doküman sayısı | 1 | 10 | Sınırsız | Sınırsız |
| Endpoint / doküman | 25 | 500 | Sınırsız | Sınırsız |
| Görünürlük modları | Public | Public + Unlisted + Private | Hepsi | Hepsi |
| Consumer group sayısı | — | 5 | Sınırsız | Sınırsız |
| Try It (proxy) | 100/gün | 5.000/gün | Sınırsız | Sınırsız |
| Mock server | 50 yanıt/gün | Sınırsız | Sınırsız | Sınırsız |
| OpenAPI/Postman import | ✓ | ✓ | ✓ | ✓ |
| Flow diagram | Basit (≤10 node) | Tam | Tam | Tam |
| Versioning + Changelog | Son 2 versiyon | Sınırsız | Sınırsız | Sınırsız |
| Custom branding | — | Logo + renk | Tam | Tam |
| Custom domain (CNAME) | — | 1 | 3 | Sınırsız |
| Yük testi (eşzamanlı / RPS / süre) | — | 25 / 100 / 5 dk | 200 / 1.000 / 15 dk | Özelleştirilebilir |
| Audit log retention | 7 gün | 90 gün | 1 yıl | Sınırsız |
| Team seats | 1 | 3 | 10 | Sınırsız |
| SSO | — | Google/Microsoft | Google/Microsoft | SAML/OIDC (v2) |
| Self-hosted / on-prem | — | — | — | ✓ |
| Destek | Community | Email | Öncelikli | Dedicated + SLA |

---

## 4. Tam Teknoloji Yığını

> **Versiyon notu:** Major.minor sabitlenmiştir. Patch versiyonu kurulum sırasında en güncel kararlı sürüm olur.

### Backend

| Bileşen | Teknoloji | Versiyon |
|---|---|---|
| Dil | Java | 21 LTS |
| Framework | Spring Boot | 3.5.x veya sonrası |
| Modülerlik | Spring Modulith | 1.3.x veya sonrası |
| Güvenlik | Spring Security | 6.x (Spring Boot ile gelen) |
| Veri erişimi | Spring Data JPA + Hibernate | 6.x |
| Migration | Flyway | 11.x |
| API spec üretimi | springdoc-openapi | 2.7.x veya sonrası |
| Rate limiting | Bucket4j | 8.x |
| Şifre hash | spring-security-crypto (Argon2id) | Spring Security ile gelen |
| Encryption | Bouncy Castle (AES-256-GCM) | 1.79+ |
| JSON | Jackson | Spring Boot ile gelen |
| Build | Gradle (Kotlin DSL) | 8.10+ |

### Frontend

| Bileşen | Teknoloji | Versiyon |
|---|---|---|
| Framework | React | 19.x |
| Build tool | Vite | 6.x veya 7.x |
| Dil | TypeScript | 5.6+ |
| Routing | TanStack Router | 1.x |
| Server state | TanStack Query | 5.x |
| Client state | Zustand | 5.x |
| Form | React Hook Form | 7.x |
| Validation | Zod | 3.x |
| UI komponentler | shadcn/ui | latest copy-paste |
| CSS | Tailwind CSS | 4.x |
| Akış diyagramı | React Flow (xyflow) | 12.x |
| Markdown editor | TipTap | 2.x |
| Syntax highlight | Shiki | 1.x |
| API client codegen | Orval | 7.x |
| i18n | react-i18next | 15.x |
| Test | Vitest + React Testing Library + Playwright (E2E) | latest |

### Desktop

| Bileşen | Teknoloji | Versiyon |
|---|---|---|
| Shell | Tauri | 2.x |
| Native dil | Rust | 1.80+ (stable) |
| Secure storage plugin | tauri-plugin-stronghold | 2.x |
| HTTP plugin | tauri-plugin-http | 2.x |
| FS plugin | tauri-plugin-fs | 2.x |
| Dialog plugin | tauri-plugin-dialog | 2.x |
| Updater plugin | tauri-plugin-updater | 2.x |

### Veri Katmanı

| Bileşen | Teknoloji | Versiyon |
|---|---|---|
| Ana DB | PostgreSQL | 17.x |
| Cache / session / rate limit | Redis | 7.4+ |
| Mesaj kuyruğu | Apache Kafka | 3.8+ veya 4.x |

### DevOps / Observability

| Bileşen | Teknoloji | Versiyon |
|---|---|---|
| Container | Docker | 27+ |
| Orchestration (dev) | Docker Compose | v2 |
| CI/CD | GitHub Actions | — |
| Integration test infra | Testcontainers | latest |
| Container security scan | Trivy | latest |
| Dependency scan | OWASP Dependency Check + Dependabot | latest |
| Logging | Logback + JSON encoder | Spring Boot ile gelen |
| Metrics | Micrometer + Prometheus | Spring Boot ile gelen / Prometheus 2.x |
| Tracing | OpenTelemetry | 1.x (latest) |
| Dashboard | Grafana | 11+ |
| Hata izleme | Sentry (veya GlitchTip) | latest |
| Uptime | Uptime Kuma veya BetterStack | latest |

### Üçüncü Parti Servisler

| Amaç | Servis |
|---|---|
| Email | Resend |
| Ödeme | Paddle veya Lemon Squeezy (kararlaştırılmadı, ödeme entegrasyonu fazında netleşir) |
| Secret manager (SaaS) | Cloud provider'ın secret manager'ı (sağlayıcı seçimi ertelendi) |
| Secret manager (on-prem) | Kubernetes Sealed Secrets veya environment + dosya |

---

## 5. Faz 0 — Yerel Geliştirme Ortamı Kurulumu

> **Bu faz SAĞLAM yapılmalı. Atlanan veya eksik bir kurulum, sonraki fazlarda iki gün ararsın.**
>
> Aşağıdaki sırayı takip et. Her adımdan sonra **verify** komutunu çalıştır; çıktı doğru değilse devam etme.

### 5.1 İşletim Sistemi Kontrolü

Kullanıcı işletim sistemini doğrula. Komutlar Windows / macOS / Linux için ayrı verildi. Geliştirici makinesi 3 OS'tan biri olmalı.

**Linux önerilir** (özellikle Ubuntu 24.04 LTS veya Fedora 41+) çünkü Docker, Postgres, Kafka gibi sunucu yazılımlarıyla daha az sürtüşme. **macOS** ikinci tercih (POSIX uyumlu). **Windows** ise WSL2 üzerinden Ubuntu kullanılmalı — native Windows üzerinde Java/Node/Docker çalışır ama Docker volume performansı ve path encoding sorunları yaşanır.

> **Eğer Windows kullanıyorsan:** Önce WSL2 + Ubuntu 24.04 kur. Tüm aşağıdaki komutlar WSL2 içinde çalıştırılacak. PowerShell'i sadece Docker Desktop ve IDE açmak için kullan.
>
> WSL2 kurulumu (PowerShell yönetici):
> ```powershell
> wsl --install -d Ubuntu-24.04
> ```
> Kurulum sonrası bilgisayarı restart et, WSL terminalini aç, Ubuntu kullanıcısı oluştur.

### 5.2 Git Kurulumu

**Versiyon:** Git 2.45+

**Kurulum:**
- **Linux/WSL:** `sudo apt update && sudo apt install -y git`
- **macOS:** `brew install git` (Homebrew yoksa: https://brew.sh)
- **Windows native:** https://git-scm.com/download/win

**Verify:**
```bash
git --version
# Beklenen: git version 2.45.x veya üstü
```

**Post-install config:**
```bash
git config --global user.name "Adın Soyadın"
git config --global user.email "email@adresin.com"
git config --global init.defaultBranch main
git config --global pull.rebase false
```

### 5.3 Java 21 LTS Kurulumu (Eclipse Temurin)

**Versiyon:** Eclipse Temurin (Adoptium) JDK 21 LTS

**Neden Temurin?** Oracle JDK lisans kısıtlamasız değil. Temurin OpenJDK build'idir, tam ücretsiz, en yaygın kullanılan kurumsal dağıtım.

**Kurulum:**
- **Linux/WSL (Ubuntu):**
  ```bash
  wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
  echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
  sudo apt update
  sudo apt install -y temurin-21-jdk
  ```
- **macOS:** `brew install --cask temurin@21`
- **Windows:** https://adoptium.net/temurin/releases/?version=21 → MSI installer indir.

**Verify:**
```bash
java --version
# Beklenen: openjdk 21.0.x ...
javac --version
# Beklenen: javac 21.0.x

echo $JAVA_HOME
# Boşsa, profile dosyana ekle (Linux/macOS):
# export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
```

### 5.4 Node.js 22 LTS Kurulumu

**Versiyon:** Node.js 22.x LTS (Iron / Jod sonrası "Active LTS" sürüm)

**Neden Node 22?** Active LTS sürüm. v24 daha yeni ama bazı kütüphanelerde uyumluluk olgunluğu henüz tam değil. v22 sweet spot.

**Yöntem:** Manuel kurulum yerine **nvm** (Node Version Manager) kullanılır ki ileride versiyon değiştirmek kolay olsun.

**Kurulum:**
- **Linux/WSL/macOS:**
  ```bash
  curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
  # Terminali kapatıp aç (veya `source ~/.bashrc` / `source ~/.zshrc`)
  nvm install 22 --lts
  nvm alias default 22
  nvm use 22
  ```
- **Windows native:** nvm-windows kullan: https://github.com/coreybutler/nvm-windows/releases

**Verify:**
```bash
node --version
# Beklenen: v22.x.x
npm --version
# Beklenen: 10.x.x
```

### 5.5 pnpm Kurulumu

**Versiyon:** pnpm 9.x+

**Neden pnpm?** npm'den hızlı, disk-dostu (sembolik link tabanlı), monorepo workspace desteği yerleşik.

**Kurulum:**
```bash
corepack enable
corepack prepare pnpm@latest --activate
```

**Verify:**
```bash
pnpm --version
# Beklenen: 9.x.x veya üstü
```

### 5.6 Docker Desktop / Docker Engine Kurulumu

**Versiyon:** Docker Engine 27+ (Docker Compose v2 dahil)

**Kurulum:**
- **Linux:** Docker Engine + Docker Compose Plugin
  ```bash
  # Ubuntu için resmi script
  curl -fsSL https://get.docker.com -o get-docker.sh
  sudo sh get-docker.sh
  sudo usermod -aG docker $USER
  # Çıkış yap-gir veya: newgrp docker
  ```
- **macOS:** Docker Desktop for Mac (https://www.docker.com/products/docker-desktop)
- **Windows:** Docker Desktop for Windows (WSL2 backend ile)

**Verify:**
```bash
docker --version
# Beklenen: Docker version 27.x+
docker compose version
# Beklenen: Docker Compose version v2.x+
docker run --rm hello-world
# Beklenen: "Hello from Docker!" mesajı
```

### 5.7 IDE Kurulumu

**IntelliJ IDEA Community Edition** (Java backend için)

**Versiyon:** IntelliJ IDEA Community Edition 2024.3+

> **Not:** Brief'inde "Eclipse" örnek vermiştin. IntelliJ IDEA Community **ücretsiz** ve Spring projelerinde Eclipse'ten belirgin biçimde daha güçlü (Spring Boot run config, Spring beans navigation, Hibernate inspection). Eğer Eclipse'e alışkınsan o da çalışır, ama IntelliJ öneriyoruz. Karar senin.

**Kurulum:**
- https://www.jetbrains.com/idea/download/?section=mac (veya işletim sisteminin sekmesi) → **Community Edition** sekmesinden indir.
- macOS'ta brew: `brew install --cask intellij-idea-ce`

**Post-install (önerilen pluginler):**
- Lombok (genellikle önceden yüklü)
- Database Tools (Community'de Lite versiyonu yok — alternatif DBeaver, aşağıda)
- Docker (Community'de var)

**VS Code** (frontend için)

**Versiyon:** VS Code latest

**Kurulum:**
- https://code.visualstudio.com/
- macOS brew: `brew install --cask visual-studio-code`

**Post-install extensions:**
- ESLint
- Prettier
- Tailwind CSS IntelliSense
- TypeScript Vue Plugin (Volar) — yok, biz React; ignore.
- TypeScript / JavaScript (yerleşik)
- vscode-icons
- GitLens

### 5.8 DBeaver (DB inspection — opsiyonel ama önerilen)

**Versiyon:** DBeaver Community 24+

**Neden?** Geliştirme sırasında DB'yi gözle incelemek zorunlu. IntelliJ Community'nin DB tool'u yok (Ultimate'ta var). DBeaver ücretsiz ve Postgres ile mükemmel çalışıyor.

**Kurulum:**
- https://dbeaver.io/download/
- macOS brew: `brew install --cask dbeaver-community`
- Linux: snap veya .deb

### 5.9 Rust Toolchain (Tauri için — Faz 17'ye kadar erteleneblir)

**Versiyon:** Rust stable (1.80+)

**Neden ertelenebilir?** Desktop uygulaması Faz 17'de geliyor. O fazdan önce Rust'a ihtiyaç yok. Sen istersen şimdi kurabilirsin, istersen o zaman.

**Kurulum (geldiği zaman):**
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
# Default kurulumu seç (1)
source $HOME/.cargo/env
```

**Tauri prerequisites (OS'a göre değişir):**
- **Linux:** `sudo apt install -y libwebkit2gtk-4.1-dev build-essential curl wget file libssl-dev libayatana-appindicator3-dev librsvg2-dev`
- **macOS:** Xcode Command Line Tools (`xcode-select --install`)
- **Windows:** Microsoft Visual Studio C++ Build Tools + WebView2

**Verify:**
```bash
rustc --version
# Beklenen: rustc 1.80.x veya üstü
cargo --version
```

### 5.10 Kurulum Doğrulama Özeti

Aşağıdaki komutların tamamı hatasız çalışmalı:

```bash
git --version             # >= 2.45
java --version            # OpenJDK 21
node --version            # v22.x
pnpm --version            # >= 9
docker --version          # >= 27
docker compose version    # v2.x
```

Hepsi yeşilse Faz 1'e geçilebilir. Bir tanesi bile fail ise önce o kurulum düzeltilir.

---

## 6. Monorepo Yapısı

Proje **tek Git repo**, **çift build sistemi** (Gradle backend için, pnpm frontend için).

### 6.1 Dizin Yapısı

```
rota/
├── .github/
│   └── workflows/             # GitHub Actions CI/CD
├── docs/                      # Proje dokümantasyonu (bu plan dahil)
│   ├── ROTA_PLAN.md
│   ├── ARCHITECTURE.md        # Detaylı mimari notlar
│   └── RUNBOOK.md             # Operasyonel runbook
├── apps/
│   ├── backend/               # Spring Boot uygulaması (Java 21)
│   │   ├── build.gradle.kts
│   │   ├── settings.gradle.kts
│   │   ├── src/main/java/com/rota/...
│   │   ├── src/main/resources/
│   │   │   ├── application.yml
│   │   │   └── db/migration/  # Flyway migrations
│   │   └── src/test/...
│   ├── web/                   # React + Vite (tarayıcı uygulaması)
│   │   ├── package.json
│   │   ├── vite.config.ts
│   │   ├── tsconfig.json
│   │   ├── src/
│   │   ├── public/
│   │   └── index.html
│   └── desktop/               # Tauri shell
│       ├── src-tauri/         # Rust kodu (Tauri komutları)
│       ├── package.json       # web app'i wrapper olarak kullanır
│       └── tauri.conf.json
├── packages/                  # Frontend'in paylaşılan TS paketleri
│   ├── api-client/            # Orval'in ürettiği TS client (auto-generated)
│   ├── ui/                    # Paylaşılan React komponentleri (shadcn)
│   ├── schemas/               # Paylaşılan Zod şemaları
│   └── config/                # ESLint, TypeScript, Tailwind preset'leri
├── infra/
│   ├── docker-compose.yml     # Geliştirme stack'i (postgres, redis, kafka, ...)
│   ├── docker-compose.observability.yml  # Grafana, Prometheus, Sentry
│   └── helm/                  # On-prem deploy için Helm chart (Faz 17+)
├── scripts/
│   ├── dev-up.sh              # Tek komutla tüm dev servisleri başlat
│   ├── dev-down.sh
│   ├── reset-db.sh
│   └── generate-api-client.sh # Backend OpenAPI'den TS client codegen
├── .gitignore
├── .editorconfig
├── README.md
├── pnpm-workspace.yaml
└── package.json               # root — workspace yöneticisi
```

### 6.2 Monorepo Araçları

- **pnpm workspaces:** `pnpm-workspace.yaml`'da `apps/web`, `apps/desktop`, `packages/*` listelenir. `pnpm install` root'tan çalıştırılır.
- **Turborepo (opsiyonel ama önerilen):** Workspace'ler arasındaki build sırasını otomatik çözer. `turbo run build` yaparsın, sıra otomatik.
- **Gradle:** `apps/backend/` içinde kendi başına. `cd apps/backend && ./gradlew bootRun` ile çalışır.

### 6.3 Faz 0 Tamamlama Görevleri (Yerel Kurulum sonrası)

> Tüm yerel kurulumlar bitince Claude Code aşağıdaki adımları sırayla yapacak.

**0.1 — Repo'yu oluştur**
```bash
mkdir -p ~/projects/rota && cd ~/projects/rota
git init
echo "# Rota" > README.md
```

**0.2 — `.gitignore` oluştur** (Java + Node + IDE + OS dosyaları için kapsamlı)

**0.3 — Dizin iskeletini oluştur** (yukarıdaki ağaca göre boş klasörler)

**0.4 — pnpm workspace setup**
```bash
# pnpm-workspace.yaml ve root package.json oluştur
```

**0.5 — Backend Spring Boot iskeleti**
```bash
# https://start.spring.io üzerinden Spring Initializr ile başlangıç projesi:
# - Gradle (Kotlin DSL)
# - Java 21
# - Spring Boot 3.5.x
# - Dependencies: Web, Data JPA, Security, Validation, Actuator, Lombok, PostgreSQL Driver, Flyway, Spring Modulith
# Çıktıyı apps/backend/ içine aç
```

**0.6 — Frontend Vite + React + TS iskeleti**
```bash
cd apps/web
pnpm create vite@latest . --template react-ts
pnpm install
# Sonra: Tailwind, shadcn, TanStack Router/Query, vs. eklenir (Faz 1'de)
```

**0.7 — Docker Compose oluştur** (`infra/docker-compose.yml`):
- postgres:17-alpine (port 5432)
- redis:7-alpine (port 6379)
- bitnami/kafka:3.8 (port 9092)
- maildev/maildev (port 1080 web UI, 1025 SMTP — local email catcher)
- adminer veya pgadmin (opsiyonel)

**0.8 — Dev script'leri yaz:** `dev-up.sh`, `dev-down.sh`.

**0.9 — Tüm servisleri ayağa kaldır, bağlantı testi:**
```bash
./scripts/dev-up.sh
# Verify:
# - psql ile postgres'e bağlan
# - redis-cli ile redis'e bağlan
# - Spring Boot uygulaması bootRun ile çalışır, /actuator/health = UP
# - Web app dev server çalışır (http://localhost:5173), beyaz sayfa görür
```

**0.10 — İlk commit:**
```bash
git add .
git commit -m "chore: initial monorepo skeleton (Phase 0)"
# GitHub'da repo oluştur (özel/private)
git remote add origin git@github.com:...
git branch -M main
git push -u origin main
```

**Kabul kriteri:** `./scripts/dev-up.sh` çalıştırıldığında tüm servisler healthy, Spring Boot actuator UP, frontend dev server açılıyor, hepsi commit'lenmiş.

---

## 7. Mimari Genel Bakış

### 7.1 Yüksek Seviye Mimari (Modular Monolith + Worker'lar)

```
                          ┌─────────────────────────┐
                          │   Web Browser / Desktop │
                          └────────────┬────────────┘
                                       │ HTTPS
                                       ▼
                          ┌─────────────────────────┐
                          │  nginx / Reverse Proxy  │  (TLS termination)
                          └────────────┬────────────┘
                                       │
                                       ▼
        ┌──────────────────────────────────────────────────────┐
        │              Spring Boot Monolith                    │
        │                                                      │
        │  ┌──────┐ ┌──────────┐ ┌──────────┐ ┌──────┐ ┌─────┐ │
        │  │ iam  │ │documents │ │endpoints │ │flows │ │ ... │ │
        │  └──────┘ └──────────┘ └──────────┘ └──────┘ └─────┘ │
        │              Spring Modulith boundaries              │
        │                                                      │
        │  Events: SpringApplicationEventPublisher (in-process)│
        │  Public events: Kafka (cross-process)                │
        └────┬──────────────────┬──────────────────┬───────────┘
             │                  │                  │
             ▼                  ▼                  ▼
        ┌────────┐         ┌────────┐         ┌────────────┐
        │Postgres│         │ Redis  │         │   Kafka    │
        │  + RLS │         │ cache  │         │   topics   │
        └────────┘         └────────┘         └─────┬──────┘
                                                    │
                          ┌─────────────────────────┼─────────────────┐
                          ▼                         ▼                 ▼
                  ┌──────────────┐        ┌──────────────┐    ┌──────────────┐
                  │ Proxy Worker │        │ Load Test    │    │ Background   │
                  │ (Try It)     │        │ Worker       │    │ Worker       │
                  │              │        │              │    │ (email, etc.)│
                  └──────────────┘        └──────────────┘    └──────────────┘
                  Same codebase,          Same codebase,      Same codebase,
                  different Spring        different Spring    different Spring
                  profile                 profile             profile
```

**Tek codebase, farklı runtime profile'ları:**
- `web` profili → REST API + UI servisi
- `proxy-worker` profili → sadece proxy ve "Try It" işleri
- `loadtest-worker` profili → sadece yük testi işleri
- `background-worker` profili → email, changelog generation, backup, vs.

Her profile aynı JAR'dan farklı bir `SPRING_PROFILES_ACTIVE` ile başlatılır. Geliştirme sırasında tek `bootRun` her şeyi tek process'te koşturabilir.

### 7.2 Veri Akışı Örneği: "Try It" İsteği (Proxy Mode)

```
1. Consumer browser → Spring REST endpoint /api/v1/proxy/execute (POST)
2. Spring (web profile):
   a. JWT doğrula → TenantContext + UserContext set
   b. Permission check: bu kullanıcı bu endpoint'i Try edebilir mi?
   c. Rate limit check (Bucket4j + Redis)
   d. Request'i Kafka topic'ine yaz: `proxy.requests`
   e. WebSocket connection üzerinden client'a "queued, request_id=..." döner
3. Spring (proxy-worker profile):
   a. Kafka topic'ten message tüket
   b. Hedef API'ye HTTP isteği at (timeout, retry policy ile)
   c. Response'u Kafka topic'ine yaz: `proxy.responses` (request_id ile)
   d. Hassas header'lar redaksiyon → activity log'a yaz
4. Spring (web profile):
   a. WebSocket üzerinden client'a response'u stream et
   b. Client browser response'u render eder
```

**Not:** v1'de WebSocket karmaşıklığını önlemek için ilk implementasyon **synchronous proxy** olabilir (Kafka kullanmadan, doğrudan HTTP call). Kafka'yı yük testinde zorunlu kullanırız. **Karar:** Proxy ilk implementasyonu sync, scale problem çıktığında async'e geçilir. Plan, sync ile başlayıp Kafka'ya geçişi kolaylaştıracak şekilde tasarlanır.

### 7.3 Deployment Topolojisi

**SaaS modu (multi-tenant):**
- Tek Postgres instance (veya RDS), tüm tenant'lar tek DB'de, RLS ile izole.
- Tek Redis cluster.
- Tek Kafka cluster.
- Web profile için 2+ replica (load balancer arkasında).
- Worker'lar bağımsız ölçeklenir.

**On-prem modu (single-tenant):**
- Tek Postgres (müşterinin makinesinde).
- Tek Redis.
- Kafka **opsiyonel** — tek-tenant'ta yük yoksa, async iş için Postgres-LISTEN/NOTIFY veya basit in-memory queue kullanılabilir (config flag).
- Web + tüm worker'lar tek JVM'de (`SPRING_PROFILES_ACTIVE=web,proxy-worker,loadtest-worker,background-worker`).

---

## 8. Multi-Tenancy ve Güvenlik Mimarisi

### 8.1 Tenant İzolasyonu — PostgreSQL Row-Level Security (RLS)

**Prensip:** Her satırda `tenant_id` kolonu + DB-seviyesinde RLS politikası. Uygulama unutsa bile yanlış tenant'ın verisi DB'den dönmez.

**Postgres rolleri:**
```sql
CREATE ROLE rota_app LOGIN PASSWORD '...';  -- Uygulamanın kullandığı rol
CREATE ROLE rota_admin LOGIN PASSWORD '...' BYPASSRLS;  -- Sadece sistem işleri için
GRANT USAGE ON SCHEMA public, audit, analytics TO rota_app, rota_admin;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO rota_app;
```

**Tenant-aware tablo örneği:**
```sql
CREATE TABLE endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    document_id UUID NOT NULL,
    method TEXT NOT NULL,
    path TEXT NOT NULL,
    ...
);

ALTER TABLE endpoints ENABLE ROW LEVEL SECURITY;
ALTER TABLE endpoints FORCE ROW LEVEL SECURITY;  -- table owner bile RLS'e tabi

CREATE POLICY tenant_isolation_endpoints ON endpoints
  USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
```

**Spring tarafında tenant context aktarımı:**

Spring'in `Connection` aldığı her noktada `SET LOCAL app.current_tenant_id = '<uuid>'` çalıştırılır. Hibernate `StatementInspector` veya Hikari `connectionInitSql` kullanarak değil, **request-scoped bir `ConnectionCustomizer`** ile yapılır.

```java
// Pseudo-code, fazlardaki implementasyonda genişler
@Component
public class TenantConnectionAspect {
    @Around("execution(* javax.sql.DataSource.getConnection(..))")
    public Connection wrap(ProceedingJoinPoint pjp) {
        Connection conn = (Connection) pjp.proceed();
        UUID tenantId = TenantContext.currentTenantId();
        if (tenantId != null) {
            try (var stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant_id = '" + tenantId + "'");
            }
        }
        return conn;
    }
}
```

**`TenantContext`:** Request-scoped bean (Java 21 virtual thread uyumlu). JWT filter tarafından her request başında set edilir, request bittiğinde otomatik temizlenir.

**Cross-tenant okuma (public doküman):** `documents` tablosu için RLS policy farklı:
```sql
CREATE POLICY document_access ON documents
  USING (
    -- Kendi tenant'ının dokümanı
    tenant_id = current_setting('app.current_tenant_id', true)::uuid
    OR
    -- Public doküman, herkes okur
    visibility = 'public'
    OR
    -- Unlisted, link ile gelinmiş (header'da access token ile)
    (visibility = 'unlisted' AND id::text = current_setting('app.current_document_access', true))
    OR
    -- Private ama kullanıcı bir consumer group üyesi
    EXISTS (
      SELECT 1 FROM consumer_group_members cgm
      JOIN consumer_group_document_access cgda ON cgm.group_id = cgda.group_id
      WHERE cgda.document_id = documents.id
        AND cgm.user_id = current_setting('app.current_user_id', true)::uuid
    )
  );
```

Alt tablolar (endpoints, categories, flow_diagrams) için RLS policy "parent document erişilebilirse erişilebilir" formatında — `EXISTS (SELECT 1 FROM documents WHERE id = endpoints.document_id)` kullanarak.

### 8.2 Encryption Stratejisi

**4 Katman:**

1. **Transport — TLS 1.3** her yerde (tarayıcı → backend, backend → DB, backend → Redis, backend → Kafka).
2. **At-rest disk — volume encryption** (deployment kararı, kod ilgilenmez).
3. **At-rest field — AES-256-GCM envelope encryption** (aşağıda detay).
4. **Hashing — Argon2id** (şifreler), **SHA-256** (access codes).

**Envelope encryption pattern:**

```
Master Key (KEK)         → KMS / Vault / env (SaaS'ta cloud KMS, on-prem'de env+disk)
       ↓ encrypts
Data Encryption Key (DEK) → Per-tenant, DB'de encrypted halde tutulur
       ↓ encrypts (AES-256-GCM)
Sensitive field           → ciphertext + IV + auth tag, DB'de bytea kolon
```

**Şifrelenen alanlar:**
- `saved_environment_variables.encrypted_value` (kullanıcının kaydettiği API key'leri)
- `oauth_clients.encrypted_secret` (API sahibinin OAuth client secret'ı)
- `webhook_endpoints.encrypted_signing_secret`
- `audit_events.encrypted_request_body` (eğer body hassas alan içeriyorsa)
- `users.refresh_token_hash` (hash, encrypt değil)

**Spring tarafı:** JPA `@Converter` ile transparan:
```java
@Convert(converter = EncryptedStringConverter.class)
private String apiKey;
```
`EncryptedStringConverter`, save'de şifreler, load'da çözer. Anahtar yönetimi `EncryptionService`'te.

### 8.3 Auth ve Session Yönetimi

**Şema:**
- **Access token:** JWT, kısa ömürlü (15 dakika), `tenant_id` + `user_id` + `roles` claim'leri.
- **Refresh token:** opaque, uzun ömürlü (30 gün), DB'de hash'li.
- **Token rotation:** Refresh kullanıldığında yeni access + yeni refresh döner, eski refresh invalidate olur.
- **Logout:** Refresh token DB'den silinir + access token blacklist'e Redis'e konulur (TTL = access expiry).

**JWT imzalama:** RS256 (asimetrik). Anahtar çifti uygulama başlangıcında env'den okunur veya rotation desteği için DB'de versiyonlu tutulur.

**OAuth2 social login (Pro+):** Google + Microsoft. Spring Security OAuth2 client desteği kullanılır.

### 8.4 Authorization Modeli

**İki seviyeli:**
1. **Organization roles:** Owner, Admin, Editor, Viewer.
2. **Consumer access:** Consumer group üyeliği + group-document-permission tablosu (view/try/loadtest).

**Spring Security'de:** `@PreAuthorize` annotation + custom `PermissionEvaluator`. Her endpoint methoduna açık permission kontrolü.

### 8.5 Rate Limiting

**Bucket4j + Redis** (distributed). Boyutlar:
- Per IP (anonim): saatte 100 istek (anti-scraping)
- Per user (authenticated): saatte 5000 istek
- Per tenant: saatte 50000 istek (Free), Pro+ daha yüksek
- Auth endpoints (login, register): per IP, 10/dakika
- Try It: tier bazında (Free 100/gün, Pro 5000/gün, vs.)

### 8.6 Audit Log

**İki ayrı log sistemi:**
- **Domain audit** (`audit.events` tablosu): kim ne değiştirdi, append-only, hash-chain.
- **Activity log** (`analytics.events` tablosu, partitioned): kim ne kullandı, yüksek hacim.

**Hash chain:**
```sql
CREATE TABLE audit.events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    actor_user_id UUID,
    actor_ip INET,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    entity_type TEXT NOT NULL,     -- 'endpoint', 'document', 'user', etc.
    entity_id UUID NOT NULL,
    action TEXT NOT NULL,           -- 'create', 'update', 'delete', 'publish', etc.
    before_state JSONB,
    after_state JSONB,
    correlation_id UUID,
    prev_hash TEXT,                 -- previous record's hash
    record_hash TEXT NOT NULL       -- SHA-256 of (this record without record_hash + prev_hash)
);

-- Append-only enforcement
CREATE RULE no_update_audit AS ON UPDATE TO audit.events DO INSTEAD NOTHING;
CREATE RULE no_delete_audit AS ON DELETE TO audit.events DO INSTEAD NOTHING;
```

**Otomatik yazma:** JPA `@EntityListener` + `@Auditable` annotation. Spring AOP ile her CUD operasyonu hook'lanır.

### 8.7 Defansif Header'lar ve Middleware

- **CSP** (Content Security Policy): `default-src 'self'; script-src 'self'; ...` (inline script yok)
- **HSTS:** `max-age=31536000; includeSubDomains; preload`
- **X-Content-Type-Options:** `nosniff`
- **X-Frame-Options:** `DENY`
- **Referrer-Policy:** `strict-origin-when-cross-origin`
- **Permissions-Policy:** kısıtlı (camera, microphone, geolocation = none)
- **CORS:** whitelist (frontend domain'leri + tenant custom domain'leri)
- **DOMPurify:** kullanıcı-girdisi HTML/Markdown render edilirken (TipTap output'u dahil)

### 8.8 Secret Management

- **Geliştirme:** `.env.local` (gitignored) + Spring `application-local.yml` (gitignored).
- **CI/CD:** GitHub Actions secrets.
- **SaaS prod (Faz 18 sonrası):** Cloud provider secret manager (sağlayıcı seçimi sonra).
- **On-prem:** Kubernetes sealed-secrets veya env+disk + Vault opsiyonel.

Hiçbir secret asla:
- Repo'ya commit edilmez (pre-commit hook ile gitleaks scan)
- Log'a yazılmaz (Logback filter)
- API response'unda dönmez (DTO'lar dikkatli yazılır)
- Frontend bundle'a girmez

---

## 9. Veritabanı Tasarımı

### 9.1 Schema'lar

- **`public`** — uygulama verisi
- **`audit`** — append-only domain audit log
- **`analytics`** — yüksek-hacim activity log (partitioned)

### 9.2 Ana Tablolar

> Aşağıda **çekirdek tablolar** verildi. Detaylı kolon tipleri ve indexler Faz 1-2'de migration dosyalarında somutlanır. Burada **veri modeli ve ilişkiler** verilmiştir.

#### `tenants` — organizasyonlar
```
id (UUID PK)
slug (TEXT UNIQUE)             -- subdomain: rotadoc.com/{slug}
name
plan (ENUM: free, pro, business, enterprise)
created_at, updated_at
encrypted_dek (BYTEA)          -- envelope encryption için per-tenant DEK
suspended_at (TIMESTAMPTZ, NULL)
```

#### `users` — bireysel kullanıcılar
```
id (UUID PK)
tenant_id (UUID FK)            -- ana tenant'ı (ileride multi-tenant user için ayrı tablo)
email (TEXT UNIQUE)
email_verified (BOOL)
password_hash (TEXT)           -- Argon2id
display_name
locale (TEXT, default 'tr')
created_at, updated_at, last_login_at
mfa_enabled (BOOL)
mfa_secret (BYTEA, encrypted)
```

#### `roles` ve `user_roles` — RBAC
```
roles: id, tenant_id, name (owner/admin/editor/viewer), system (bool — sistem rolleri silinemez)
user_roles: user_id, role_id
```

#### `documents` — API dokümanları
```
id (UUID PK)
tenant_id (UUID FK)
slug (TEXT)                    -- URL'de: /{tenant}/{document-slug}
name
description (TEXT)
visibility (ENUM: public, unlisted, private)
unlisted_access_code_hash (TEXT, NULL)  -- SHA-256, sadece unlisted
current_version_id (UUID FK, NULL)      -- şu an published olan versiyon
created_at, updated_at, published_at (NULL)
created_by (user_id)
branding (JSONB)               -- logo URL, primary color, etc.
```

#### `document_versions` — versiyon kayıtları
```
id (UUID PK)
document_id (UUID FK)
version_label (TEXT)           -- 'v1.0', 'v2.0', 'v1.1', vs.
status (ENUM: draft, published, archived)
created_at, published_at (NULL)
changelog_md (TEXT)            -- API sahibinin yazdığı changelog
auto_diff_json (JSONB)         -- otomatik üretilen diff
```

#### `categories`
```
id (UUID PK)
tenant_id (UUID FK)
document_version_id (UUID FK)
name, description, sort_order
```

#### `endpoints`
```
id (UUID PK)
tenant_id (UUID FK)
document_version_id (UUID FK)
category_id (UUID FK, NULL)
method (ENUM: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS)
path (TEXT)                    -- '/users/{id}/orders'
summary, description_md
auth_type (ENUM: none, bearer, api_key, basic, oauth2)
auth_config (JSONB)            -- header adı, OAuth flow, vs.
sort_order
mock_enabled (BOOL)
deprecated (BOOL)
created_at, updated_at, created_by
```

#### `endpoint_parameters`
```
id, endpoint_id (FK), name, location (ENUM: path, query, header, cookie), data_type, required, description, default_value, example, sort_order
```

#### `endpoint_request_bodies`
```
id, endpoint_id (FK), content_type, schema_json, example_json
```

#### `endpoint_responses`
```
id, endpoint_id (FK), status_code, description, content_type, schema_json, example_json
```

#### `endpoint_mock_responses`
```
id, endpoint_id (FK), status_code, headers_json, body_json, weight (probability), conditions_json (NULL — koşullu mock için)
```

#### `environments`
```
id, document_version_id, name (test/staging/prod), base_url, is_production_warn (BOOL)
```

#### `flow_diagrams`
```
id (UUID PK)
tenant_id (UUID FK)
document_version_id (UUID FK)
name, description
nodes_json (JSONB)             -- React Flow node array
edges_json (JSONB)             -- React Flow edge array
created_at, updated_at
```

#### `consumer_groups`
```
id, tenant_id, name, description
```

#### `consumer_group_members`
```
group_id, user_id (or email + invited_at)
```

#### `consumer_group_document_access`
```
group_id, document_id, can_view (BOOL), can_try (BOOL), can_loadtest (BOOL)
```

#### `consumer_group_endpoint_access` (override)
```
group_id, endpoint_id, can_view, can_try, can_loadtest
```

#### `saved_environments` (consumer'ın kaydettiği değişken setleri)
```
id, user_id, document_version_id, name, encrypted_variables (BYTEA — JSON şifreli)
```

#### `try_it_history`
```
id, user_id, endpoint_id, executed_at, method, url, status_code, latency_ms, request_summary_json, response_summary_json
```

#### `load_test_jobs`
```
id, tenant_id, document_version_id, endpoint_id (or scenario_id), created_by, status (ENUM: queued, running, completed, failed, killed), config_json, results_summary_json, started_at, finished_at
```

#### `domain_verifications`
```
id, tenant_id, domain (TEXT), verification_method (ENUM: dns_txt, http_file, email), verification_token, verified_at (NULL), expires_at (verified + 90 gün)
```

#### `custom_domains` (Pro+)
```
id, tenant_id, domain, document_id (NULL = tüm tenant için), ssl_cert_id (Let's Encrypt yönetimi), status
```

#### `subscriptions`
```
id, tenant_id, plan, status, provider (paddle/lemonsqueezy), external_subscription_id, current_period_end, cancel_at_period_end
```

#### `audit.events` (yukarıda verildi)
#### `analytics.events_YYYY_MM` (partitioned by month)
```
id, tenant_id, occurred_at, event_type, actor_user_id, properties_json
```

### 9.3 Indexler

Sık sorgulanan kolonlarda:
- Tüm tablolarda `(tenant_id, ...)` composite indexler (RLS sorgularını hızlandırır)
- `documents.slug` UNIQUE within tenant
- `users.email` UNIQUE globally
- `endpoints.(document_version_id, method, path)` UNIQUE
- `audit.events.(tenant_id, occurred_at DESC)`
- `analytics.events.(tenant_id, event_type, occurred_at DESC)`

### 9.4 Migration Stratejisi

- **Flyway** ile yönetim. Dosya adı: `V001__init.sql`, `V002__add_documents.sql`, ...
- Her migration **transactional**, **idempotent değil** (Flyway tarafından bir kere koşar).
- **Production-safe migrations:** uzun süreli `ALTER TABLE`'lar `CONCURRENTLY` veya küçük adımlarla.
- **RLS politikaları** ayrı migration dosyalarında, açık ve okunaklı SQL ile.
- **Asla geri alma migration'ı yazılmaz** (forward-only). Geri dönmek gerekirse yeni bir "düzeltme" migration'ı.

---

## 10. Backend Modülleri (Spring Modulith)

Spring Modulith, paket-bazlı modül sınırları zorlar. Modüller arası iletişim **Spring `ApplicationEventPublisher`** üzerinden olur. Modüller asla diğerinin internal sınıflarını import edemez (ArchUnit testleri bunu doğrular).

**Paket yapısı:**
```
com.rota
├── common/             # cross-cutting: tenant context, encryption, audit base
├── iam/                # identity, users, roles, auth
├── tenancy/            # tenants, plans, subscriptions snapshot
├── documents/          # documents, versions, publishing
├── endpoints/          # endpoints, categories, parameters
├── flows/              # flow diagrams
├── consumers/          # consumer groups, access codes, invitations
├── proxy/              # Try It server-side execution
├── mock/               # mock server
├── loadtest/           # load test jobs
├── billing/            # payment provider, subscription lifecycle
├── audit/              # domain audit log
├── analytics/          # activity log, usage stats
├── notifications/      # email, in-app
├── domains/            # custom domain CNAME + Let's Encrypt
└── importer/           # OpenAPI / Postman / cURL parsers
```

Her modülün internal yapısı:
```
modulename/
├── package-info.java          # @ApplicationModule(displayName = "...")
├── api/                       # public API (Spring service interfaces, DTOs, events)
├── internal/                  # implementation — diğer modüller import edemez
├── jpa/                       # entities (internal)
└── web/                       # @RestController (public — frontend için endpoint)
```

**Public events (modüller arası iletişim için):**
- `iam`: `UserRegisteredEvent`, `UserLoggedInEvent`
- `tenancy`: `TenantCreatedEvent`, `TenantPlanChangedEvent`, `TenantSuspendedEvent`
- `documents`: `DocumentPublishedEvent`, `DocumentVersionCreatedEvent`
- `endpoints`: `EndpointCreatedEvent`, `EndpointUpdatedEvent`, `EndpointDeletedEvent`
- `proxy`: `ProxyRequestExecutedEvent`
- `loadtest`: `LoadTestStartedEvent`, `LoadTestCompletedEvent`
- `billing`: `SubscriptionActivatedEvent`, `SubscriptionCanceledEvent`

**Olay tüketicileri örneği:**
- `audit` modülü hemen her event'i dinler ve audit log'a yazar
- `notifications` modülü `DocumentPublishedEvent`'i dinler ve consumer'lara mail atar
- `analytics` modülü `ProxyRequestExecutedEvent`'i dinler ve activity log'a yazar

**Modulith'in faydası:** ArchUnit testleri ile "endpoints modülü iam.internal.UserRepository'yi import etmiş" gibi sınır ihlalleri CI'da yakalanır.

---

## 11. Frontend Mimarisi

### 11.1 Sayfa Ağacı

```
/
├── /                                    # Landing page (marketing)
├── /pricing
├── /docs                                # public dokümanların marketplace'i
├── /explore                             # public dokümanlar arama
├── /signup
├── /login
├── /forgot-password
├── /reset-password?token=...
├── /verify-email?token=...
├── /invitation/accept?token=...
│
├── /app                                 # giriş yapmış kullanıcı için
│   ├── /dashboard                       # özet (dokümanlar, son aktiviteler)
│   ├── /documents                       # API sahibi: kendi dokümanları listesi
│   │   └── /:docId
│   │       ├── /overview                # genel bilgi, branding, görünürlük
│   │       ├── /versions                # versiyon yönetimi + changelog
│   │       ├── /categories              # kategori CRUD
│   │       ├── /endpoints
│   │       │   ├── /                    # liste
│   │       │   ├── /new                 # ekle (manuel/cURL/OpenAPI/Postman)
│   │       │   └── /:endpointId         # düzenle (tabs: General/Params/Body/Responses/Mock/Code)
│   │       ├── /flows                   # akış diyagramları (React Flow editor)
│   │       │   └── /:flowId
│   │       ├── /environments            # test/staging/prod URL'leri
│   │       ├── /access                  # consumer group + permission yönetimi
│   │       ├── /loadtest                # yük testi
│   │       └── /settings                # custom domain, branding, transfer
│   ├── /tenant
│   │   ├── /members                     # organization üyeleri
│   │   ├── /roles                       # roller
│   │   ├── /billing                     # subscription, fatura
│   │   ├── /audit                       # audit log viewer
│   │   └── /api-keys                    # ileride: Rota'nın kendi API'si için
│   └── /profile
│       ├── /account
│       ├── /security                    # MFA, password change
│       ├── /sessions                    # aktif session'lar
│       └── /saved-environments          # tüketici: kaydettiği API key'ler
│
└── /d                                   # public/consumer-facing doc view
    └── /:tenantSlug
        └── /:docSlug
            ├── /                        # overview + kategoriler
            ├── /flow/:flowId            # interaktif akış diyagramı
            ├── /endpoint/:endpointId    # endpoint detay + Try It
            └── /changelog               # versiyon geçmişi
```

**Custom domain modunda:** `https://api-docs.musteri.com/` doğrudan `/d/{tenantSlug}/{docSlug}` rotasına eşlenir, ama URL gizli kalır.

### 11.2 Komponent Kütüphanesi

**packages/ui/** içinde:
- shadcn/ui base komponentleri (Button, Input, Card, Dialog, Sheet, vs.) — copy-paste, kütüphane değil
- Domain komponentleri: `<EndpointMethodBadge>`, `<ParameterEditor>`, `<HeaderEditor>`, `<CodeSampleViewer>`, `<TryItPanel>`, `<FlowDiagramEditor>`, `<MarkdownEditor>`, `<EnvironmentSelector>`
- Layout komponentleri: `<AppShell>`, `<DocumentSidebar>`, `<ConsumerLayout>`

### 11.3 State Yönetimi

- **Server state:** TanStack Query. Her API hook Orval tarafından otomatik üretilir (`useGetEndpoints`, `useCreateEndpoint`, ...). Cache invalidation otomatik (Orval mutation hook'ları cache key'leri biliyor).
- **Client state:** Zustand store'ları (auth state, current tenant, current document, theme, locale).
- **Form state:** React Hook Form.
- **URL state:** TanStack Router (params, search params type-safe).

### 11.4 OpenAPI Codegen Pipeline

```
1. Backend Spring Boot çalışır, /v3/api-docs endpoint'i OpenAPI JSON üretir
2. scripts/generate-api-client.sh:
   - Backend'i dev modda çalıştırır (veya statik spec dosyası kullanır)
   - Orval CLI çalışır: openapi.json → packages/api-client/src/generated/
3. Frontend bu paketten hook'ları import eder
4. Backend endpoint değişirse: codegen tekrar çalışır, TS compile error frontend'i günceller
```

### 11.5 Theme + i18n

- **Light + Dark mode** (system tercihi varsayılan).
- **Türkçe + İngilizce** (react-i18next, JSON dosyaları `packages/ui/src/locales/`).
- **Tarih/sayı formatları** locale'e bağlı (date-fns + Intl).

---

## 12. Desktop Uygulama Mimarisi

### 12.1 Tauri Shell

`apps/desktop/` web app'i Tauri içine sarar. Frontend kodu **birebir aynı** — Tauri ile koşulduğunda ek "Desktop API"leri açılır.

**Rust tarafı (src-tauri):**
- `main.rs` — Tauri uygulamasını başlatır, command'ları register eder
- `commands/` — frontend'in çağırabileceği Rust fonksiyonları:
  - `try_it_native(request)` — CORS'suz, native HTTP isteği at
  - `keychain_store(key, value)` — OS keychain'e güvenli yaz
  - `keychain_retrieve(key)` — oku
  - `file_save_response(content, suggested_filename)` — response'u dosyaya kaydet
  - `localhost_test(url)` — localhost endpoint'lerine erişim

**Frontend tarafı:**
- `lib/desktop.ts` — `if (window.__TAURI__)` kontrolü, varsa native command'lar kullanılır, yoksa web fallback
- Try It component'i platform-aware: web'de proxy, desktop'ta native

### 12.2 Native Plugin'ler

- `tauri-plugin-stronghold` — IOTA Stronghold ile güvenli local key storage (master password ile şifreli)
- `tauri-plugin-http` — Rust reqwest tabanlı CORS'suz HTTP client
- `tauri-plugin-fs` — controlled filesystem access
- `tauri-plugin-dialog` — açma/kaydetme dialog'ları
- `tauri-plugin-updater` — kendi sunucumuzdan signed updates

### 12.3 Auto-Update

- Tauri updater config'inde update endpoint: `https://updates.rotadoc.com/desktop/{platform}/{current_version}`
- Bu endpoint, signed manifest döner: yeni versiyon varsa, yeni binary URL + ed25519 signature
- Binary indirilir, signature doğrulanır, kullanıcı onayıyla restart

### 12.4 Code Signing

- **macOS:** Apple Developer Program ($99/yıl), notarization
- **Windows:** EV code signing sertifikası ($300/yıl), Microsoft SmartScreen reputation için gerekli
- **Linux:** AppImage + GPG signature

Bu yatırımlar Faz 17 sonunda yapılır; geliştirme sırasında self-signed yeterli.

---

## 13. Anahtar Özelliklerin Detaylı Tasarımı

### 13.1 Try It (Proxy) — Üç Mod

**Mod A — Server Proxy (varsayılan, web):**

```
Browser → POST /api/v1/proxy/execute
  {
    endpointId,
    environmentId,
    pathParams: { id: 123 },
    queryParams: { limit: 10 },
    headers: { "X-API-Key": "sk_..." },  // hassas
    body: { ... }
  }
  
Backend (proxy module):
  1. Authorize (kullanıcının bu endpoint'i Try yetkisi var mı?)
  2. Rate limit check
  3. Endpoint config + environment'tan target URL build et
  4. HTTP client ile hedef API'ye istek at (timeout: 30s, max body: 10MB)
  5. Response'u client'a stream et
  6. Activity log'a yaz (hassas header'lar redact)
  7. Bellekteki key'i sıfırla (request bitince GC'ye bırakılmaz, explicit zero-fill)
```

**Güvenlik kuralları:**
- Outbound istekler için **SSRF koruması:** private IP range'ler (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, ::1) **bloklu**. Sadece public IP'lere veya domain ownership doğrulanmış custom internal domain'lere izin.
- **Timeout** her layer'da: connection 10s, total 30s.
- **Max response size:** 10MB.
- **Header allowlist:** Bazı header'lar (örn. `Host`, `Content-Length`) override edilemez.
- **Sensitive logging:** `Authorization`, `X-API-Key`, `Cookie` ve regex eşleşen alanlar log'da `[REDACTED]`.

**Mod B — Browser-Direct (CORS gerekir):**

API sahibi bu modu seçerse, frontend doğrudan `fetch()` ile hedef API'ye gider. Anahtar bizim sunucumuza hiç gelmez. UI'da "Bu doküman browser-direct modunda; API'nin CORS başlıklarında bu domain'in olması gerekir" uyarısı.

**Mod C — Desktop-Only:**

Frontend Tauri kontrolü yapar; web'deyse "Desktop uygulaması gerekiyor" mesajı + indirme linki gösterir, Try butonunu disable eder.

**Bu üç modun seçimi:** Doküman seviyesinde + endpoint seviyesinde override. Default: A.

### 13.2 Mock Server

**Endpoint başına config:**
- `mock_enabled` boolean
- Bir veya birden çok `endpoint_mock_responses` kaydı
- Weighted random (default eşit) veya koşullu (query param X varsa, mock Y)

**Try It mock modunda:**
- Backend hedef API'ye gitmez, mock response'lardan birini seçer ve döner.
- Latency simülasyonu: mock kaydında `simulated_latency_ms` field'ı.

**Mock yanıt template'leri (basit):**
- Sabit JSON body
- `{{faker.email}}`, `{{faker.uuid}}`, `{{request.param.id}}` placeholder'ları (v1.x'te genişletilir)

### 13.3 Akış Diyagramı Editör

**React Flow tabanlı.** Node tipleri:
- **Start** (yeşil dairesel) — akışın başlangıcı
- **Endpoint** (kutu, method renkli) — bir API endpoint'i çağırır. Tıklanabilir, endpoint dokümanına atlar.
- **Condition** (rombus) — response/state üzerinde dallanma
- **Wait** (saat ikonu) — `wait_seconds` veya `wait_until_time`
- **Loop** (döngüsel ok) — bir önceki node'a geri dön, max iteration
- **End** (kırmızı dairesel) — akış sonu

**Edge label format:**
- Düz ok: koşulsuz geçiş
- Koşullu: `response.status == 200`, `body.state == 'pending'`
- Loop ok: `retry < 5 && body.state != 'completed'`

**Save format (JSONB):**
```json
{
  "nodes": [
    { "id": "n1", "type": "start", "position": {...} },
    { "id": "n2", "type": "endpoint", "data": { "endpointId": "...", "label": "GET /max-order-id" }, "position": {...} },
    ...
  ],
  "edges": [
    { "id": "e1", "source": "n1", "target": "n2" },
    { "id": "e2", "source": "n2", "target": "n3", "data": { "condition": "response.status == 200" } }
  ]
}
```

**Tüketici görünümü:**
- Aynı React Flow, ama editable false.
- Endpoint node'una tıklayınca `/d/{tenant}/{doc}/endpoint/{id}` sayfasına atlar.
- Minimap + zoom + pan.

### 13.4 Versioning + Otomatik Changelog

**Yaşam döngüsü:**
- Yeni doküman → otomatik `v1.0` draft versiyon
- API sahibi içeriği doldurur → "Publish" → `published`
- Yeni versiyon: "Clone from v1.0" → `v2.0 draft` (içerik kopyalanır, editlenir)
- Publish edildiğinde: önceki published versiyon `archived` olur, yeni publish edilen `published` olur
- Tüketici URL'leri: `/d/{tenant}/{doc}/` → mevcut published, `/d/{tenant}/{doc}/v1.0` → spesifik versiyon

**Auto-diff algoritması:**
- İki versiyon arasında: endpoint'leri `(method, path)` üzerinden eşle
- Eklenenler / silinenler listele
- Eşleşenlerde: parametre seti diff (added/removed/changed), request body schema diff, response schema diff
- Markdown formatında changelog draft üret, API sahibi düzenler veya doğrudan kullanır

### 13.5 Yük Testi

**Domain ownership verification akışı:**

1. API sahibi "Domain ekle" → `api.musteri.com` girer
2. Sistem `_rota-verify.api.musteri.com` için random token üretir, kullanıcıya gösterir
3. Kullanıcı DNS'e TXT kaydı ekler
4. Sistem dakikada bir DNS sorgular (max 24 saat); başarılıysa `verified_at` set edilir
5. Alternatif yöntemler: HTTPS file (`/.well-known/rota-verify.txt`), email (admin@/webmaster@/postmaster@)
6. Doğrulama 90 günde bir otomatik yenilenir

**Yük testi job tasarımı:**

```
Submit job:
  - target: endpoint_id veya scenario (akış diyagramı temelli)
  - users: eşzamanlı sanal kullanıcı sayısı
  - rate: hedef RPS
  - duration_seconds
  - ramp_up_seconds (opsiyonel)
  
Sistem:
  1. Tier limit kontrolü
  2. Domain ownership doğrulanmış mı?
  3. API sahibinin son onayı (modal: "Bu test {RPS} RPS yük basacak, devam?")
  4. Kafka topic'e job push
  5. loadtest-worker(s) job'u tüketir, paralel HTTP isteği üretir
  6. Real-time metrikler Redis'e yazılır + WebSocket ile UI'ya stream
  7. Job bitince: aggregate sonuç DB'ye yazılır (p50/p95/p99 latency, error rate, throughput)
  
Kill switch:
  - Job sahibi "Durdur" diyebilir
  - Sistem: target API >30s boyunca >%50 5xx döndüyse otomatik durdur (circuit breaker)
  - Her istek `User-Agent: Rota-LoadTest/1.0` header'ı taşır (target sahip identifiable)
```

**Worker implementation:** Java HttpClient + virtual threads (Java 21). Her sanal kullanıcı bir virtual thread.

### 13.6 OpenAPI / Postman / cURL Import

**OpenAPI 3.x import:**
- `swagger-parser` kütüphanesi
- Parse → in-memory model → endpoints/categories tablolarına insert
- Auth scheme'leri tanı (bearerAuth, apiKey, oauth2)
- Servers → environments olarak ekle
- Tags → categories olarak ekle

**Postman Collection v2.1:**
- Postman SDK / `postman-collection` parser (JS) yok Java'da; manuel JSON parsing
- Folder hierarchy → categories
- Requests → endpoints

**cURL paste:**
- Frontend'de basit parser (popüler cURL'lerin %95'ini kapsayan regex + state machine)
- Method, URL (host + path + query), headers, body extract et
- "Hangi endpoint'e eklensin?" sor

### 13.7 Code Samples Generation

Backend endpoint kayıt edildiğinde, runtime'da (cache'li) şu dillere otomatik sample üretilir:
- **cURL:** template engine ile
- **Python:** `requests` kütüphanesi
- **JavaScript:** `fetch` (browser) ve `fetch` (Node 22+)
- **Java:** `java.net.http.HttpClient`
- **Go:** `net/http`
- **PHP:** `curl_*` veya Guzzle
- **C#:** `HttpClient`

API sahibi her dil için manuel override yazabilir.

### 13.8 Custom Domain (Pro+)

**Akış:**
1. Pro+ kullanıcı `docs.musteri.com` ekler
2. Sistem domain ownership doğrulaması ister (yukarıdaki gibi)
3. Doğrulandıktan sonra kullanıcı DNS'e CNAME ekler: `docs.musteri.com → cnames.rotadoc.com`
4. Sistem ACME / Let's Encrypt ile sertifika alır (cert-manager benzeri otomasyon — Spring Boot için `acme4j` veya nginx sidecar)
5. nginx config dinamik güncellenir (veya Caddy ile auto-TLS)
6. Tüketici `https://docs.musteri.com` → backend doğru tenant'a route eder

---

## 14. CI/CD, Observability ve Operasyonel Altyapı

### 14.1 GitHub Actions Workflows

`.github/workflows/`:

- **`backend-ci.yml`** — PR'da Java tarafı: lint (Checkstyle), compile, test, integration test (Testcontainers), security scan (OWASP DC + Trivy)
- **`frontend-ci.yml`** — PR'da web: lint (ESLint), type-check (tsc --noEmit), test (Vitest), build
- **`e2e-ci.yml`** — main merge sonrası: Playwright E2E (gerçek Postgres + Redis + Kafka container)
- **`security-ci.yml`** — haftalık scheduled: dependency scan, secret scan (gitleaks)
- **`release.yml`** — tag push: Docker image build & push, desktop binary build (matrix: mac/linux/windows), GitHub Release oluştur

### 14.2 Observability Local Stack

`infra/docker-compose.observability.yml`:
- **Prometheus** (metrics scrape)
- **Grafana** (dashboard) — preconfigured dashboards: HTTP latency, DB, Redis, Kafka lag, business KPIs
- **Tempo** veya **Jaeger** (tracing)
- **Loki** (log aggregation) — opsiyonel
- **GlitchTip** veya **Sentry self-hosted** (error tracking)

Bu stack opsiyonel başlatılır (`docker compose -f observability.yml up`). Geliştirme sırasında debug için.

### 14.3 Production Observability (deferred — Faz 18)

Sağlayıcı kararı ertelendiği için somut karar yok. Olası seçenekler:
- **Self-hosted:** Prometheus + Grafana + Loki + Tempo + Sentry → ucuz, yönetim yükü var
- **Managed:** Grafana Cloud + Sentry Cloud → kolay, daha pahalı

### 14.4 Database Backup

**SaaS:**
- Postgres WAL archiving (continuous) → S3-compatible storage
- Günlük full backup
- 30 gün retention
- **Aylık restore test'i** (otomatize, ayrı staging env'e restore + smoke test)

**On-prem:** Müşteri kendi sorumluluğu. Dokümantasyonda örnek `pg_dump` cron job verilir.

### 14.5 Logging

- **Format:** JSON, Logback `LogstashEncoder`
- **Fields:** timestamp, level, logger, thread, trace_id, span_id, tenant_id, user_id, message, mdc
- **Sensitive data:** custom Logback filter, regex ile redact
- **Local:** stdout
- **Prod:** stdout → container log → Loki / cloud log service

---

## 15. Test Stratejisi

### 15.1 Backend Testleri

- **Unit testler** (JUnit 5 + Mockito): her servis sınıfının iş mantığı
- **Slice testler** (Spring Boot test slice annotations): `@DataJpaTest`, `@WebMvcTest`
- **Integration testler** (Testcontainers):
  - **Gerçek Postgres** (`postgres:17-alpine`) — RLS policy testleri burada zorunlu
  - **Gerçek Redis** (`redis:7-alpine`)
  - **Gerçek Kafka** (`bitnami/kafka:3.8`) — event publish/consume testleri
- **Security testler:** Spring Security `@WithMockUser` + RLS cross-tenant leak testleri
- **Architecture testler:** Spring Modulith + ArchUnit, modül sınırı ihlalleri

**Coverage hedefi:** Servis layer'da %80+, kritik güvenlik kodunda %95+.

### 15.2 Frontend Testleri

- **Unit + component:** Vitest + React Testing Library
- **E2E:** Playwright — kritik akışlar:
  - Signup → email verify → first document → first endpoint → publish
  - Consumer: doküman gör → Try It → response gör
  - Flow editor: node ekle → edge çiz → kaydet → render
- **Visual regression** (opsiyonel, ileride): Playwright screenshot diff

### 15.3 Security Testler

Zorunlu CI gates:
- **RLS leak test:** Tenant A oturumunda Tenant B'nin satırlarına erişim DENİYOR, hatasız reddediliyor
- **Visibility test:** Anonim user public görür, private göremez
- **Auth bypass test:** JWT olmadan korumalı endpoint 401 döner
- **Rate limit test:** Limit aşımı 429 döner
- **CSRF test:** Cross-origin POST reddedilir
- **SSRF test:** Proxy'den private IP'ye istek reddedilir

### 15.4 Load Tests (kendi API'miz için)

- **k6** scripts: `infra/loadtests/`
- Senaryolar: doküman listele, endpoint oluştur, Try It (mock target)
- Hedef: p95 < 300ms, error rate < 0.1%
- Faz 18'de prod-benzeri env'e karşı çalıştırılır

---

## 16. Geliştirme Fazları (Yol Haritası)

> **Genel kural:** Her faz **kabul kriterleri** karşılanmadan sonraki faza geçilmez. "Hızlı ilerleyip sonra düzeltiriz" yaklaşımı **bu projede geçerli değildir** — özellikle güvenlik ve multi-tenancy fazlarında.

### Faz 0 — Yerel Ortam + Monorepo İskeleti
**Bölüm 5 ve 6'da detaylandırıldı.**

**Kabul kriteri:**
- Tüm tool'lar kurulu, verify komutları yeşil
- Repo oluşturuldu, ilk commit pushlandı
- `./scripts/dev-up.sh` → tüm container'lar healthy
- Backend `./gradlew bootRun` → `/actuator/health` UP
- Frontend `pnpm dev` → http://localhost:5173 yüklenir

---

### Faz 1 — IAM + Tenancy + Multi-Tenancy Foundation

> **Bu faz, projenin temelidir. ATLANAMAZ ve EKSIK BIRAKILAMAZ.** Multi-tenancy bug'ı sonradan düzeltilemez.

**Görevler (sırayla):**

1.1 Database migrations: `tenants`, `users`, `roles`, `user_roles`, `audit.events` tabloları
1.2 RLS politikaları için temel script: Postgres rolleri (`rota_app`, `rota_admin`), RLS ENABLE/FORCE pattern'ı
1.3 Spring Modulith setup: `common`, `iam`, `tenancy`, `audit` modüllerinin paket-info.java'ları, ApplicationModule annotations
1.4 `common.tenant.TenantContext` (request-scoped) implementation
1.5 `common.encryption.EncryptionService` — envelope encryption (KEK env'den, DEK per-tenant DB'de)
1.6 Hibernate / HikariCP `Connection` hook'u: her connection alındığında `SET LOCAL app.current_tenant_id` çalıştır
1.7 Spring Security konfigürasyonu: JWT filter, refresh token mekanizması, password hash (Argon2id)
1.8 `iam` modülü API'leri: register, login, refresh, logout, password reset, email verify
1.9 `tenancy` modülü API'leri: tenant create (signup ile birlikte), tenant get, plan info
1.10 `audit` modülü: AOP aspect ile JPA entity changes capture, hash chain implementation
1.11 Cross-tenant leak testleri (Testcontainers + gerçek Postgres) — **zorunlu**
1.12 Frontend: signup, login, password reset sayfaları (shadcn/ui + Tailwind kurulumu burada)
1.13 OpenAPI spec → frontend client codegen pipeline (Orval) — ilk çalıştırma
1.14 Rate limiting (Bucket4j + Redis) — auth endpoint'leri için sıkı limit
1.15 Email gönderimi: Resend integration + transactional template'ler (verify email, password reset)

**Kabul kriteri:**
- Bir kullanıcı signup olabiliyor, email verify ediyor, login oluyor
- JWT alıyor, refresh çalışıyor
- İki ayrı tenant oluşturulduğunda biri diğerinin verisini DB'de göremiyor (RLS testi pas)
- Audit log her CUD operasyonunda yazılıyor, hash chain integrity test pas
- Rate limit çalışıyor (test: 11. login attempt 429)
- ArchUnit modül sınırı testleri pas
- Encryption: `users.refresh_token_hash`, `tenants.encrypted_dek` DB'de bytea, decrypt ile orijinal değer geri geliyor

**Bu fazın çıktısı:** Çalışan, güvenli auth + tenancy temeli. Geri kalan her şey bu üzerine inşa edilir.

---

### Faz 2 — Documents + Categories + Endpoints (Backend CRUD)

**Görevler:**

2.1 Migrations: `documents`, `document_versions`, `categories`, `endpoints`, `endpoint_parameters`, `endpoint_request_bodies`, `endpoint_responses`, `environments` + RLS policies
2.2 Modüller: `documents`, `endpoints`
2.3 REST API: documents CRUD, document_versions CRUD (create/clone/publish/archive), categories CRUD, endpoints CRUD, parameters CRUD
2.4 Permission middleware: `@PreAuthorize` ile Owner/Admin/Editor/Viewer kontrolü
2.5 `consumer_groups`, `consumer_group_members`, `consumer_group_document_access`, `consumer_group_endpoint_access` migrations + RLS
2.6 Consumer access modülü: invitation flow (email + token), group management

**Kabul kriteri:**
- Bir tenant 1+ doküman oluşturabiliyor, draft → publish edebiliyor
- Endpoint'ler kategoriler altında listeleniyor
- Diğer tenant'ın dokümanları RLS ile gizli
- Permission denied case'leri 403 dönüyor (test ile)
- OpenAPI spec güncellendi, frontend client regenerate edildi, compile temiz

---

### Faz 3 — Frontend Doc + Endpoint Editor

**Görevler:**

3.1 Layout: `<AppShell>` (sol sidebar, üst nav, content area)
3.2 Dashboard sayfası
3.3 Documents listesi + yeni doküman wizard
3.4 Document overview sayfası (genel bilgi düzenleme, branding placeholder, görünürlük seçimi)
3.5 Endpoints liste sayfası (kategori bazlı grouping)
3.6 Endpoint editör sayfası (tabs: General / Parameters / Body / Responses / Code Samples placeholder)
3.7 Parameter editor komponenti (path/query/header parameters, drag-drop reorder)
3.8 Path variable kolay ekleme butonu (cursor pozisyonuna `{paramName}` insert)
3.9 Header editor (suggest: Accept, Content-Type, Authorization)
3.10 Request body editor (JSON schema builder veya monaco editor + Zod validation)
3.11 Response editor (status code per response)
3.12 Categories yönetimi
3.13 Markdown editor (TipTap) entegrasyonu, endpoint description için

**Kabul kriteri:**
- Kullanıcı bir API doküman'ı sıfırdan UI üzerinden oluşturabiliyor
- Endpoint'leri ekliyor, parametre/header/body tanımlıyor
- Tüm değişiklikler optimistic update + TanStack Query invalidation ile akıcı
- TypeScript hatasız, ESLint temiz, Lighthouse performance > 90

---

### Faz 4 — OpenAPI / Postman / cURL Import

**Görevler:**

4.1 Backend: OpenAPI 3.x parser modülü (`importer` modülü)
4.2 Backend: Postman Collection v2.1 parser
4.3 Backend: cURL parser (basit case'ler, %95 coverage)
4.4 Frontend: import wizard (dosya yükle veya yapıştır → önizleme → confirm)
4.5 Import edilenleri preview'da göster, kullanıcı kategori atayabilsin
4.6 Endpoint duplicate detection (aynı method+path varsa overwrite/skip/merge)
4.7 OpenAPI EXPORT: kendi dokümanlarımızı OpenAPI 3 spec olarak dışa aktarma

**Kabul kriteri:**
- Petstore OpenAPI spec'i import edilebiliyor, tüm endpoint'ler doğru parse oluyor
- Postman collection (en yaygın test koleksiyon) import çalışıyor
- 10 farklı cURL örneği parse oluyor

---

### Faz 5 — Try It (Proxy Mode A)

**Görevler:**

5.1 `proxy` modülü
5.2 REST endpoint: `POST /api/v1/proxy/execute`
5.3 SSRF koruması: private IP block, allowlist host check
5.4 HTTP client (Java HttpClient, virtual threads)
5.5 Timeout, max body size enforcement
5.6 Sensitive header redaction (regex + allowlist)
5.7 Activity log yazımı (`analytics.events` partitioned tabloya)
5.8 Try It history kaydı (`try_it_history`)
5.9 Frontend: `<TryItPanel>` komponenti — endpoint editor sayfasında ve consumer view'da
5.10 Environment selector (test/staging/prod), prod uyarısı
5.11 Request preview (cURL representation generated client-side from form)
5.12 Response viewer (status, headers, body — JSON pretty, syntax highlighted via Shiki)
5.13 History panel: son istekler, replay butonu
5.14 Saved environments: kullanıcı kendi key set'lerini kaydedebilir (encrypted-at-rest)
5.15 Rate limit ön kontrol (frontend disabled state + tooltip)
5.16 Free tier günlük 100 Try It limiti enforce

**Kabul kriteri:**
- Public bir API'ye (httpbin.org gibi) Try It çalışıyor, response geliyor
- Latency UI'da ölçülüp gösteriliyor
- Sensitive header'lar (Authorization) log'da `[REDACTED]` görünüyor
- Private IP'ye istek atmaya çalışan request 400 hatası ile reddediliyor
- 101. günlük istek 429 dönüyor (Free tier)

---

### Faz 6 — Code Samples Generation

**Görevler:**

6.1 Backend: code sample generator service (template-based)
6.2 Diller: cURL, Python requests, JS fetch, Java HttpClient, Go net/http, PHP, C#
6.3 Sample her endpoint için cache'li üretilir (Redis), endpoint değişince invalidate
6.4 Frontend: `<CodeSampleViewer>` komponenti, dil tab'ları, Shiki ile highlight, copy button
6.5 API sahibi her dil için manual override yazabilir (UI'da editor)

**Kabul kriteri:**
- 7 dilde sample üretiliyor, syntax doğru (test cases ile doğrula)
- Override edilmiş sample, generated'ı geçer
- Copy button çalışıyor

---

### Faz 7 — Flow Diagram Editor

**Görevler:**

7.1 `flows` modülü + `flow_diagrams` tablo migration
7.2 Frontend: React Flow setup (`apps/web/src/features/flows/`)
7.3 Custom node komponentleri: Start, Endpoint, Condition, Wait, Loop, End
7.4 Endpoint node'una endpoint seçici (mevcut document endpoint'lerden)
7.5 Edge label editör (koşul ifadesi yazma, basit DSL veya serbest text)
7.6 Save/load JSON format
7.7 Read-only consumer view: minimap, zoom, pan, node click → endpoint sayfasına navigate
7.8 Diagram listesi (bir doküman birden çok akış içerebilir)
7.9 Auto-layout butonu (dagre layout)

**Kabul kriteri:**
- Editör'de sürükle-bırak ile diyagram oluşturulabiliyor
- Save sonrası reload edildiğinde aynı görünüyor
- Consumer view'da endpoint node'una tıklayınca doğru sayfaya gidiyor
- Free tier'da node sayısı > 10 olduğunda upgrade prompt

---

### Faz 8 — Mock Server

**Görevler:**

8.1 Migrations: `endpoint_mock_responses`
8.2 Backend: `mock` modülü, endpoint başına mock toggle
8.3 Mock response CRUD UI
8.4 Try It mock mode: hedef API'ye gitmeden mock dönüyor
8.5 Weighted random selection
8.6 Latency simulation
8.7 Simple template variables (`{{request.param.X}}`, `{{faker.uuid}}`)

**Kabul kriteri:**
- Mock-enabled endpoint Try It'te mock döner, gerçek API'ye gitmez
- Weighted random dağılımı doğru (test)
- Variable interpolation çalışıyor

---

### Faz 9 — Consumer-Facing Portal + Permission Enforcement

**Görevler:**

9.1 Public route'lar: `/d/{tenant}/{doc}` ve alt sayfalar
9.2 Anonim erişim middleware: public dokümanlar JWT'siz erişilebilir
9.3 Unlisted access: URL'de access token, doğrulama
9.4 Consumer login UI + invitation accept flow
9.5 Permission enforcement: can_view / can_try / can_loadtest checks
9.6 Branding: tenant logo + primary color (CSS variables)
9.7 SEO: public dokümanlar için meta tags + sitemap.xml + robots.txt
9.8 SSR / pre-rendering: public doküman ana sayfaları için basit server-rendered HTML (search engines + first-paint için)
9.9 Marketplace: `/explore` sayfası, public dokümanlar listesi, arama

**Kabul kriteri:**
- Anonim bir kullanıcı public doküman görüyor, private göremez
- Unlisted link çalışıyor, link olmadan erişim yok
- Invited consumer login olup private dokümana erişiyor
- Sitemap güncel, Google indexleyebiliyor (manuel test)

---

### Faz 10 — Versioning + Auto-Changelog

**Görevler:**

10.1 Version lifecycle UI: draft / published / archived states
10.2 "Clone to new version" akışı (içerik kopyalanır)
10.3 Diff algoritması: iki versiyon arası endpoint/parameter/schema farkları
10.4 Auto-changelog generator (Markdown çıktı)
10.5 Changelog editor (auto-generated draft'ı kullanıcı edit edebilir)
10.6 Consumer view changelog sayfası

**Kabul kriteri:**
- v1.0 → v2.0 clone çalışıyor
- v1.0'da ekleyip silersen, diff doğru gösteriyor
- Changelog auto-generation Türkçe ve İngilizce çıktı veriyor

---

### Faz 11 — Domain Verification + Load Testing

**Görevler:**

11.1 Migrations: `domain_verifications`, `load_test_jobs`
11.2 `domains` modülü: TXT/HTTP file/email verification
11.3 DNS check job (background worker, dakikalık)
11.4 `loadtest` modülü
11.5 Job queue: Kafka topic `loadtest.jobs`
11.6 Load test worker (ayrı profile): virtual threads, hedef RPS sağlar
11.7 Real-time metrics: Redis pub-sub, WebSocket frontend'e stream
11.8 Kill switch (manual + auto circuit breaker)
11.9 Results storage + UI: p50/p95/p99, error rate, throughput graph
11.10 Tier limit enforcement
11.11 API sahibi explicit onay modal

**Kabul kriteri:**
- DNS TXT verification çalışıyor (test domain'le)
- Doğrulanmamış domain'e yük testi attempt 403
- Pro tier 25 user / 100 RPS / 5 dk limit enforce
- Kill switch UI'dan basıldığında 5 saniye içinde tüm requests durur
- Sonuçlar accurate (test: known endpoint, beklenen latency)

---

### Faz 12 — Try It Modes B (Browser-Direct) + C (Desktop-Only)

**Görevler:**

12.1 Backend: doküman config'ine `try_it_mode` field (A/B/C/per-endpoint override)
12.2 Frontend: mod-aware Try It panel
12.3 Mode B: doğrudan `fetch()`, CORS uyarısı UI'da
12.4 Mode C: web'de disabled + indir mesajı

**Kabul kriteri:**
- Mode B: API sahibi seçtiğinde, frontend network tab'ında istek doğrudan hedef API'ye gidiyor (bizim backend'e değil)
- Mode C: web'de Try butonu disabled, "Desktop gerekli" mesajı

---

### Faz 13 — Custom Domain + Branding

**Görevler:**

13.1 Migrations: `custom_domains`
13.2 CNAME setup UI (kullanıcıya talimat)
13.3 Let's Encrypt automation (acme4j veya Caddy sidecar)
13.4 nginx dinamik routing (custom domain → tenant resolution)
13.5 Branding UI: logo upload, primary color picker, custom CSS injection (sanitized)
13.6 Pro+ gate

**Kabul kriteri:**
- `docs.example.com` → CNAME → cnames.rotadoc.com setup'ı doğru tenant'a yönlendiriyor
- SSL otomatik alınıyor, valid
- Branding değişiklikleri consumer view'da yansıyor

---

### Faz 14 — Email + Notifications

**Görevler:**

14.1 `notifications` modülü
14.2 Email template'leri: verify, password reset, invitation, document published, weekly digest
14.3 Resend integration
14.4 In-app notification (basit, bell icon + unread count)
14.5 Email preferences (opt-in/out per type)

**Kabul kriteri:**
- 5 email tipi de doğru gönderiliyor
- Resend webhook'ları (delivery, bounce, complaint) doğru handle ediliyor

---

### Faz 15 — Payment + Billing

**Görevler:**

15.1 Provider seçimi netleştir: Paddle vs Lemon Squeezy
15.2 PaymentProvider interface
15.3 Subscription state machine
15.4 Webhook handler (signature verification)
15.5 Plan limits enforcement (her tier feature'ı için)
15.6 Billing UI: plan seçim, upgrade/downgrade, invoice history
15.7 Pro-rated charges (provider handles, biz reflect)
15.8 Downgrade graceful handling (örn. Pro→Free: fazla dokümanlar read-only)
15.9 Trial period (Pro 14 gün)

**Kabul kriteri:**
- Test ödeme yapılabiliyor (sandbox)
- Webhook geldiğinde subscription state DB'de güncelleniyor
- Limit aşımı (örn. Free'de 2. doküman) doğru engelleniyor

---

### Faz 16 — Audit Log UI + Activity Log + Analytics

**Görevler:**

16.1 Audit log viewer UI: filtrelenebilir, kim/ne/ne zaman
16.2 Activity log partitioning + retention job (eski partition'lar drop)
16.3 Basic analytics: doküman view count, endpoint try count, top endpoints
16.4 Export (CSV/JSON) audit log (Pro+)

**Kabul kriteri:**
- Audit log son 100 olayı UI'da gösteriyor, filtreler çalışıyor
- 91 gün önceki activity log otomatik silindi (Pro tier)

---

### Faz 17 — Desktop Application (Tauri)

**Görevler:**

17.1 Rust toolchain kurulum (Phase 0'da ertelenmiş)
17.2 Tauri 2.x init: `apps/desktop/src-tauri/`
17.3 Frontend kodu web'den paylaşılır (Vite build'i Tauri'ye bind)
17.4 Tauri commands: try_it_native, keychain_store/retrieve, file_save_response, localhost_test
17.5 `tauri-plugin-stronghold` ile local secret storage
17.6 Platform detection in frontend (`window.__TAURI__`)
17.7 Offline mode: TanStack Query persistent cache
17.8 Auto-updater config
17.9 Code signing setup (developer/build için self-signed, prod için certificate)
17.10 Multi-platform build pipeline (GitHub Actions matrix)

**Kabul kriteri:**
- macOS, Windows, Linux için installer üretiliyor
- Try It native HTTP üzerinden çalışıyor (network tab'ında bizim backend yok)
- Key'ler OS keychain'de, restart sonrası kalıyor
- Auto-update: eski versiyon → yeni versiyon update çalışıyor (signed)

---

### Faz 18 — Polish, Security Audit, Production Hazırlık

**Görevler:**

18.1 Pen-test (dış kaynak) — bütçe kararına göre
18.2 Lighthouse + accessibility audit (WCAG AA hedef)
18.3 Performance optimizasyonu: bundle size, image optimization, code splitting
18.4 Cloud sağlayıcı seçimi + IaC (Terraform veya Pulumi)
18.5 Production secret manager setup (cloud provider'ın çözümü)
18.6 Production deployment: nginx + backend cluster + Postgres + Redis + Kafka
18.7 Monitoring + alerting setup (PagerDuty veya BetterStack ile on-call)
18.8 Status page (status.rotadoc.com)
18.9 Documentation: kullanıcı dokümantasyonu (Rota'yı Rota ile dokümante et — dogfooding)
18.10 Legal: ToS, Privacy Policy, KVKK uyum metni, Cookie policy
18.11 Marketing site: landing, pricing, blog placeholder
18.12 Beta launch → public launch

**Kabul kriteri:**
- Pen-test raporundaki kritik/yüksek bulgular düzeltildi
- Production env stabil çalışıyor (1 hafta uptime > %99.9)
- Yedek + restore test başarılı
- İlk gerçek müşteri onboarding'i sorunsuz

---

## 17. Açık Riskler ve Ertelenmiş Kararlar

| Konu | Durum | Karar Zamanı |
|---|---|---|
| Cloud provider seçimi | Ertelendi | Faz 18 |
| Data residency çözümü (TR) | Ertelendi | Faz 18 |
| Ödeme provider (Paddle vs Lemon Squeezy) | Faz 15 başında karar | Faz 15 |
| Pen-test bütçesi | Karar verilmedi | Faz 18 |
| Kafka on-prem'de opsiyonel mi? | Tasarım gereği opsiyonel, implementasyon Faz 17 öncesi netleşir | Faz 17 |
| llms.txt + MCP server desteği | v2 | — |
| GraphQL / WebSocket / gRPC | v2 | — |
| AI ile doküman analizi (PDF/Word) | v2 | — |
| Zero-knowledge Try mode | v1.x | Faz 12 sonrası |
| HashiCorp Vault | v2'de gerekirse | — |
| ClickHouse (analytics için) | v2 | — |

---

## 18. Sözlük

| Terim | Anlam |
|---|---|
| **Tenant** | Organizasyon. Bir API sahibi şirket veya bireysel hesap. |
| **Owner / Admin / Editor / Viewer** | Tenant içi roller. |
| **Consumer** | API tüketicisi. Dokümanları okur, Try yapar. |
| **Consumer Group** | Tüketicilerin gruplandığı erişim birimi. Permission group level'da verilir. |
| **Document** | Bir API'nin Rota'daki temsili. Versiyonlanır. |
| **Document Version** | Bir document'in belirli bir versiyonu (draft/published/archived). |
| **Endpoint** | Bir API endpoint'i (method + path). |
| **Flow Diagram** | Endpoint'leri sıralayan/dallandıran interaktif diyagram. |
| **Try It** | Tüketicinin/sahibinin endpoint'e test isteği atma özelliği. |
| **Mock Server** | Hedef API'ye gitmeden, kayıtlı yanıt döndürme. |
| **Load Test** | Doğrulanmış API'ye yük basma. |
| **RLS** | Row-Level Security. PostgreSQL özelliği, satır seviyesi yetki. |
| **DEK / KEK** | Data / Key Encryption Key. Envelope encryption pattern. |
| **Modular Monolith** | Tek deploy artifact'ı, içinde net modül sınırları. |
| **Audit Log** | Append-only, hash-chained, kim ne değiştirdi log'u. |
| **Activity Log** | Yüksek hacim, kim ne kullandı log'u. |

---

> **Son not Claude Code'a:**
>
> 1. Bu plan tek-doğru-referans. Bir konuda belirsizlik varsa **kullanıcıya sor**, varsayım üretme.
> 2. **Faz sırasına uy.** Faz N kabul kriterleri karşılanmadan Faz N+1'e geçme.
> 3. Her fazın sonunda kullanıcıya **özet + commit listesi + bir sonraki faza geçiş onayı** iste.
> 4. Güvenlik testleri (RLS leak, auth bypass, SSRF, rate limit) **opsiyonel değil**. CI'da gate olarak duracak.
> 5. Migration dosyaları **forward-only**. Geri alma yok, "düzeltme migration'ı" yaz.
> 6. Hassas alan eklerken kendine sor: **bu alan encrypt edilmeli mi?** Şüpheliyse kullanıcıya sor.
> 7. Yeni bir kütüphane eklerken **lisans + güncellik + popülerlik** kontrol et. Maintained olmayan kütüphane ekleme.
> 8. Her commit anlamlı bir atomic değişiklik olsun. "WIP" commit'leri prod branch'e gitmez.
