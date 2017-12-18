# ETL: Twitter Analysis

<b>Alumnos:</b>
<ul><li>Luciano Hammoe</li><li>Pablo E. Bragan</li></ul>

## Crear el jar que contiene la aplicaci&oacute;n y sus dependencias
```bash
$ sbt clean assembly
```

## Usar spark-submit para correr la aplicaci&oacute;n

```bash
$ spark-submit \
  --class "es.arjon.FromCsvToParquet" \
  --master 'local[*]' \
  target/scala-2.11/us-stock-analysis-assembly-0.1.jar
```

```bash
$ spark-submit \
  --class "es.arjon.RunAll" \
  --master 'spark://master:7077' \
  --driver-class-path /dataset/postgresql-42.1.4.jar \
  target/scala-2.11/us-stock-analysis-assembly-0.1.jar
```
