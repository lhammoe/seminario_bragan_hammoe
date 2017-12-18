# ETL: Twitter Analysis

<b>Alumnos:</b>
<ul><li>Luciano Hammoe</li><li>Pablo E. Bragan</li></ul>

## Crear el jar que contiene la aplicaci&oacute;n y sus dependencias
```bash
$ sbt clean assembly
```

## Usar spark-submit para correr la aplicaci&oacute;n

###Obtencion de Tweets y env&iacute;o a Kafka

####Obtenci&oacute;n de Tweets

#####Argumentos:
<ol>
    <li>brokers: lista de uno o m&aacute;s brokers de kafka</li>
    <li>topic: topic de kafka</li>
    <li>savingInterval: intervalo de env&iacute;o de tweets a Kafka</li>
    <li>filtersTrack: palabras de filtro para los tweets.</li>
    <li>filtersLocations: coordenadas longitud,latitud de a par. Es un rectangulo que representa a un area. El primer punto es el inferior izquierdo y el segundo el superior derecho.</li>
</ol>

```bash
$ spark-submit \
  --class "ar.bh.TweetsGenerator" \
  --master 'spark://master:7077' \
  target/scala-2.11/twitter-analysis-assembly-0.1.jar \
  kafka:9092 \
  tweets \
  200 \
  nba,spurs,ginobilli \
  -117.16,32.69,-66.97,48.98
```
Se ejecutar&aacute; un proceso que no tiene fin y mientras se llenará la cola de Kafka.

####ETL sobre Kafka

#####Argumentos:
<ol>
    <li>brokers: lista de uno o m&aacute;s brokers de kafka</li>
    <li>topic: topic de kafka</li>
    <li>path: directorio donde se guardar&aacute; la lectura de tweets</li>
</ol>

```bash
$ spark-submit \
  --class "ar.bh.TwitterStreamingETL" \
  --master 'spark://master:7077' \
  target/scala-2.11/twitter-analysis-assembly-0.1.jar \
  kafka:9092 \
  tweets \
  dataset/output/parquet

