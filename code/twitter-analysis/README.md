# ETL: Twitter Analysis

<b>Alumnos:</b>
<ul><li>Luciano Hammoe</li><li>Pablo E. Bragan</li></ul>

## Crear el jar que contiene la aplicaci&oacute;n y sus dependencias
```bash
$ sbt clean assembly
```

## Usar spark-submit para correr la aplicaci&oacute;n

###Obtencion de Tweets y env&iacute;o a Kafka

####Argumentos:
<ol>
    <li>brokers: lista de uno o m&aacute;s brokers de kafka</li>
    <li>topic: topic de kafka</li>
    <li>path: directorio donde se guardar&aacute; la lectura de tweets</li>
    <li>savingInterval: intervalo de env&iacute;o de tweets a Kafka</li>
    <li>filtersTrack: palabras de filtro para los tweets.</li>
    <li>filtersLocations: coordenadas longitud,latitud de a par. Es un rectangulo que representa a un area.</li>
</ol>

```bash
$ spark-submit \
  --class "ar.bh.TweetsGenerator" \
  --master 'local[*]' \
  target/scala-2.11/twitter-analysis-assembly-0.1.jar \
  kafka:9092 \
  tweets \
  /dataset/output/parquet \
  200 \
  nba,san antonio\ spurs,ginobilli \
  -123.75,47.872144,-80.332031,25.641526
```
