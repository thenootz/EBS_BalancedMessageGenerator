# CONTEXT.md — EBS Message Generator

## Despre proiect

Acesta este un proiect Java standalone (fără dependențe externe, fără Storm) care generează aleator seturi echilibrate de **publicații** și **subscripții** pentru un sistem Event-Based (EBS), ca parte a temei practice de la cursul **Sisteme Bazate pe Evenimente** (Emanuel Onica, UAIC Iași).

---

## Cerința temei (rezumat)

- Generare aleatoare de publicații și subscripții cu structură fixă
- Posibilitate de configurare a:
  - numărului total de mesaje
  - ponderii pe frecvența câmpurilor din subscripții (ex. 90% din subscripții să conțină câmpul `company`)
  - ponderii operatorului `=` pentru cel puțin un câmp (ex. 70% din subscripțiile cu `company` să folosească `=`)
- Paralelizare prin thread-uri pentru eficientizarea generării
- Evaluare comparativă a timpilor (1 thread vs. 4 thread-uri)
- Salvare în fișiere text

**Nu** se cere implementarea unui mecanism de matching sau a unei topologii Storm.

---

## Structura publicației (fixă)

```
{(company,"Google");(value,90.00);(drop,10.00);(variation,0.7300);(date,2.2.2022)}
```

| Câmp        | Tip      | Valori posibile                                      |
|-------------|----------|------------------------------------------------------|
| `company`   | String   | Google, Apple, Microsoft, Amazon, Tesla, Meta, Netflix, Nvidia, Intel, AMD |
| `value`     | double   | [10.0, 500.0]                                        |
| `drop`      | double   | [0.0, 50.0]                                          |
| `variation` | double   | [-5.0, 5.0]                                          |
| `date`      | LocalDate| 2020-01-01 .. 2024-12-31                             |

## Structura subscripției (câmpuri opționale cu operator)

```
{(company,=,"Google");(value,>=,90.00);(variation,<,0.8000)}
```

Operatori: `company` → `=` / `!=` | câmpuri numerice/date → `=`, `!=`, `>=`, `<=`, `>`, `<`

---

## Fișiere implementate

Toate fișierele sunt în pachetul `ebs.generator`:

| Fișier                | Rol                                                                 |
|-----------------------|---------------------------------------------------------------------|
| `Main.java`           | Entry point; configurare frecvențe, lansare generare, verificare, benchmark |
| `GeneratorConfig.java`| POJO cu toți parametrii (N, frecvențe, threads, paths)             |
| `MessageGenerator.java`| Logica de generare publicații + subscripții, paralelizare         |
| `Publication.java`    | Model publicație cu câmpuri fixe + `toString()`                    |
| `Subscription.java`   | Model subscripție cu câmpuri opționale + `toString()`              |
| `BenchmarkRunner.java`| Rulare comparativă 1 vs. N thread-uri, afișare timpi               |
| `FileWriter.java`     | Scriere buffered în fișiere text                                    |

---

## Algoritm cheie — frecvențe deterministe (Fisher-Yates)

**Nu** se folosește `random()` per subscripție (Bernoulli), conform indicației din enunț. În schimb:

1. Se calculează exact `ceil(freq × N)` sloturi per câmp
2. Se construiește array `[0, N)`, se amestecă cu **Fisher-Yates shuffle**, se iau primii `count` indici
3. Din acei indici se aleg exact `ceil(eqFreq × count)` care folosesc `=`
4. Thread-urile procesează segmente disjuncte din `results[]` — fără sincronizare la scriere

Rezultat verificat: frecvențele obținute sunt **exacte** (0% eroare față de țintă).

---

## Configurare curentă (în `Main.java`)

```java
// Frecvența câmpurilor în subscripții
fieldFreq.put("company",   0.90);
fieldFreq.put("value",     0.80);
fieldFreq.put("drop",      0.60);
fieldFreq.put("variation", 0.50);
fieldFreq.put("date",      0.40);

// Frecvența operatorului "=" per câmp
eqFreq.put("company", 0.70);
eqFreq.put("value",   0.30);
```

---

## Compilare și rulare

```bash
# Compilare
mkdir -p out
javac -d out $(find src -name "*.java")

# Rulare (100k mesaje, 4 thread-uri, output în ./output)
java -Xmx256m -cp out ebs.generator.Main 100000 4 output
```

**Argumente CLI:** `[totalMesaje] [nrThreaduri] [directorOutput]` — toate opționale, default: `1000000 4 output`

---

## Rezultate benchmark obținute

### N = 100 000 publicații + 100 000 subscripții

| Fază                        | 1 thread  | 4 thread-uri | Speedup  |
|-----------------------------|-----------|--------------|----------|
| Publications – generare     | 35 ms     | 17 ms        | ~2.1×    |
| Publications – scriere      | 687 ms    | 422 ms       | ~1.6×    |
| Subscriptions – generare    | 831 ms    | 485 ms       | ~1.7×    |
| Subscriptions – scriere     | 139 ms    | 95 ms        | ~1.5×    |
| **Total**                   | **1692 ms** | **1019 ms** | **~1.7×** |

**CPU:** OpenJDK 21, Ubuntu 24.04, x86-64 | **Heap:** `-Xmx256m`

---

## Setup Eclipse

1. **File → New → Maven Project** (simple project, skip archetype)
   - Group Id: `ebs.generator` | Artifact Id: `MessageGenerator`
2. **pom.xml** — fără dependențe externe, doar compiler Java 17:
   ```xml
   <properties>
     <maven.compiler.source>17</maven.compiler.source>
     <maven.compiler.target>17</maven.compiler.target>
   </properties>
   ```
3. Creează pachetul `ebs.generator` în `src/main/java` și copiază cele 7 fișiere `.java`
4. **Run As → Java Application** pe `Main.java`
5. Argumente opționale în **Run Configurations → Arguments**: ex. `100000 4 output`

---

## Diagrama de clase (Mermaid)

Salvată în `class-diagram.mermaid`. Relații principale:
- `Main` și `BenchmarkRunner` creează `GeneratorConfig` + `MessageGenerator`, apelează `FileWriter`
- `MessageGenerator` are referință persistentă la `GeneratorConfig` (câmpul `cfg`)
- `MessageGenerator` instanțiază `Publication` și `Subscription`

---

## Status proiect

- [x] Implementare completă toate clasele
- [x] Verificare frecvențe exacte (testată la 100k și 500k mesaje)
- [x] Benchmark 1 vs. 4 thread-uri documentat în README
- [x] README.md complet
- [x] Diagramă de clase UML (Mermaid)
- [x] Setup Eclipse documentat
- [ ] Configurare frecvențe din fișier `.properties` (îmbunătățire posibilă)
- [ ] Integrare opțională într-un proiect Storm existent (generator ca spout)
