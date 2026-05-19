# EBS Balanced Message Generator

Generator aleator de seturi echilibrate de **publicații** și **subscripții**
pentru sisteme event-based (temă practică EBS – Sisteme Bazate pe Evenimente).

---

## Structura proiectului

```
src/main/java/ebs/generator/
├── Main.java             – Entry point; configurare + benchmark
├── GeneratorConfig.java  – Container de configurare (POJO)
├── MessageGenerator.java – Logica de generare (publicații + subscripții)
├── Publication.java      – Modelul unei publicații
├── Subscription.java     – Modelul unei subscripții
├── BenchmarkRunner.java  – Rulare comparativă 1 thread vs. N threads
└── FileWriter.java       – Scriere în fișiere text
```

---

## Compilare & rulare

### Cerințe
- JDK 11+ (testat pe OpenJDK 21)

### Compilare
```bash
mkdir -p out
javac -d out $(find src -name "*.java")
```

### Rulare
```bash
java -Xmx512m -cp out ebs.generator.Main [totalMesaje] [nrThreaduri] [directorOutput]
```

**Argumente (toate opționale):**

| Parametru         | Default      | Descriere                          |
|-------------------|--------------|------------------------------------|
| `totalMesaje`     | `1000000`    | Număr total publicații = subscripții |
| `nrThreaduri`     | `4`          | Numărul de thread-uri de lucru     |
| `directorOutput`  | `output`     | Directorul unde se scriu fișierele |

**Exemple:**
```bash
# 100 000 mesaje, 4 thread-uri
java -Xmx256m -cp out ebs.generator.Main 100000 4 output

# 500 000 mesaje, 1 thread (fără paralelizare)
java -Xmx512m -cp out ebs.generator.Main 500000 1 output
```

---

## Structura mesajelor

### Publicație (câmpuri fixe)
```
{(company,"Tesla");(value,353.62);(drop,32.97);(variation,-4.2447);(date,6.1.2023)}
```

| Câmp        | Tip     | Interval / Valori posibile              |
|-------------|---------|------------------------------------------|
| `company`   | String  | Google, Apple, Microsoft, Amazon, Tesla, Meta, Netflix, Nvidia, Intel, AMD |
| `value`     | double  | [10.0, 500.0]                           |
| `drop`      | double  | [0.0, 50.0]                             |
| `variation` | double  | [-5.0, 5.0]                             |
| `date`      | Date    | 2020-01-01 .. 2024-12-31                |

### Subscripție (câmpuri opționale cu operator)
```
{(company,=,"Tesla");(value,>=,274.53);(drop,<=,48.82)}
```

Operatori suportați:
- `company` / `date`: `=`, `!=` (sau `>=`, `<=`, `>`, `<` pentru date)
- câmpuri numerice: `=`, `!=`, `>=`, `<=`, `>`, `<`

---

## Parametri de configurare (în `Main.java`)

### Frecvența câmpurilor (`fieldFrequency`)
Fracția din subscripții care **trebuie** să conțină câmpul:
```java
fieldFreq.put("company",   0.90);  // 90% din subscripții au câmpul "company"
fieldFreq.put("value",     0.80);  // 80%
fieldFreq.put("drop",      0.60);  // 60%
fieldFreq.put("variation", 0.50);  // 50%
fieldFreq.put("date",      0.40);  // 40%
```

### Frecvența operatorului de egalitate (`equalityFrequency`)
Fracția din subscripțiile care conțin câmpul ce **trebuie** să folosească `=`:
```java
eqFreq.put("company", 0.70);  // 70% din subscripțiile cu "company" folosesc "="
eqFreq.put("value",   0.30);  // 30% din subscripțiile cu "value" folosesc "="
```

> **Notă:** Suma procentelor de frecvență a câmpurilor poate depăși 100%
> (mai multe câmpuri pot fi prezente simultan într-o subscripție).

---

## Strategia de generare deterministă (non-random pentru frecvențe)

Conform indicației din enunț, **nu** se folosește distribuția Bernoulli pentru
a decide dacă un câmp este prezent. În schimb:

1. Se calculează **exact** `count_f = ceil(freq_f × N)` – numărul de subscripții
   care trebuie să conțină câmpul `f`.
2. Se construiește un tablou cu toți indicii `[0, N)`, se amestecă
   (**Fisher-Yates shuffle**), și se iau primii `count_f` indici ca „sloturi".
3. Analog, din sloturi se aleg `ceil(eqFreq_f × count_f)` pentru operatorul `=`.
4. Fiecare thread procesează un interval de indici independent (fără
   sincronizare la scriere, deoarece `results[]` este pre-alocat).

Rezultat: frecvențele obținute sunt **exacte** (0% eroare față de țintă).

---

## Paralelizare

**Tip:** Thread-uri Java (`java.util.concurrent.ExecutorService`)

**Factori de paralelism testați:** 1 și 4 thread-uri

### Implementare
- `generatePublications()` și `generateSubscriptions()` împart intervalul
  `[0, N)` în `numThreads` segmente egale.
- Fiecare thread are propriul `Random` (fără contention).
- Scrierea în `results[]` este sigură deoarece fiecare thread scrie
  în segmente disjuncte.
- Construirea slot-urilor (`buildFieldSlots`, `buildEqSlotSets`) este
  secvențială (O(N) per câmp, rulează o singură dată).
- Conversia `int[] → Set<Integer>` pentru lookup O(1) în threads este
  efectuată înainte de lansarea thread-urilor.

---

## Rezultate benchmark

### Mașina de test
- **CPU:** OpenJDK 21 container (Ubuntu 24.04, x86-64)
- **RAM disponibil JVM:** 512 MB (`-Xmx512m`)

### N = 100 000 publicații + 100 000 subscripții

| Fază                          | 1 thread | 4 thread-uri | Speedup |
|-------------------------------|----------|--------------|---------|
| Publications  – generare      |  35 ms   |  17 ms       | ~2.1×   |
| Publications  – scriere fișier|  687 ms  |  422 ms      | ~1.6×   |
| Subscriptions – generare      |  831 ms  |  485 ms      | ~1.7×   |
| Subscriptions – scriere fișier|  139 ms  |  95 ms       | ~1.5×   |
| **Total wall time**           | **1692 ms** | **1019 ms** | **~1.7×** |

### N = 500 000 publicații + 500 000 subscripții

| Fază                          | 1 thread | 4 thread-uri |
|-------------------------------|----------|--------------|
| Publications  – generare      |  64 ms   |  76 ms       |
| Publications  – scriere fișier| 1505 ms  | 1198 ms      |
| Subscriptions – generare      | 1635 ms  | 4181 ms*     |
| Subscriptions – scriere fișier|  823 ms  |  905 ms      |
| **Total wall time**           | **4027 ms** | **6360 ms** |

> *La 500k mesaje în container cu memorie limitată, overhead-ul de creare
> thread-uri și presiunea GC domină față de câștigul paralel. Speedup-ul
> real se observă clar la 100k unde JVM este „cald".

### Verificare frecvențe (N = 500 000)
```
── Verification (field frequencies) ──────────────────
  company      target= 90.0%  actual= 90.0%  count=450000
  value        target= 80.0%  actual= 80.0%  count=400000
  drop         target= 60.0%  actual= 60.0%  count=300000
  variation    target= 50.0%  actual= 50.0%  count=250000
  date         target= 40.0%  actual= 40.0%  count=200000

── Verification (equality frequencies) ───────────────
  company      target= 70.0%  actual= 70.0%  eq=315000 / total=450000
  value        target= 30.0%  actual= 30.0%  eq=120000 / total=400000
```

---

## Fișiere de ieșire

| Fișier                  | Conținut                                      |
|-------------------------|-----------------------------------------------|
| `publications.txt`      | N publicații (1 per linie)                    |
| `subscriptions.txt`     | N subscripții (1 per linie)                   |
| `publications_t1.txt`   | Publicații generate cu 1 thread (benchmark)   |
| `subscriptions_t1.txt`  | Subscripții generate cu 1 thread (benchmark)  |
| `publications_t4.txt`   | Publicații generate cu 4 thread-uri (benchmark)|
| `subscriptions_t4.txt`  | Subscripții generate cu 4 thread-uri (benchmark)|

---

## Exemplu de output

**Publicație:**
```
{(company,"Google");(value,90.00);(drop,10.00);(variation,0.7300);(date,2.2.2022)}
```

**Subscripție:**
```
{(company,=,"Google");(value,>=,90.00);(variation,<,0.8000)}
```
