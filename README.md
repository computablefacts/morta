# Morta

![Maven Central](https://img.shields.io/maven-central/v/com.computablefacts/morta)
[![Build Status](https://travis-ci.com/computablefacts/morta.svg?branch=master)](https://travis-ci.com/computablefacts/morta)
[![codecov](https://codecov.io/gh/computablefacts/morta/branch/master/graph/badge.svg)](https://codecov.io/gh/computablefacts/morta)

Morta is a proof-of-concept Java implementation of [Snorkel](https://www.snorkel.org/).

## Usage

First, and unlike Snorkel, Morta automatically creates Labeling Functions from
user-provided gold labels (if need be, these functions are then automatically merged
with handcrafted Labeling Functions). Then, the Labeling Functions are used to train
a Generative Model. At last, a Discriminative Model is trained. The output of each
step is saved as an XML file.

### Creating Gold Labels

The format of a single Gold Label is :

```
{
    "id": "<uuid>",
    "label": "<model_name>",
    "data": "<text>",
    "is_true_positive": <true|false>,
    "is_true_negative": <true|false>,
    "is_false_positive": <true|false>,
    "is_false_negative": <true|false>
}
```

The Gold Labels must be grouped together as a [ND-JSON](http://ndjson.org/) file :

```
{"id":"<uuid>","label":"<model_name>","data":"<text>","is_true_positive":<true|false>,"is_true_negative":<true|false>,"is_false_positive":<true|false>,"is_false_negative":<true|false>}
{"id":"<uuid>","label":"<model_name>","data":"<text>","is_true_positive":<true|false>,"is_true_negative":<true|false>,"is_false_positive":<true|false>,"is_false_negative":<true|false>}
{"id":"<uuid>","label":"<model_name>","data":"<text>","is_true_positive":<true|false>,"is_true_negative":<true|false>,"is_false_positive":<true|false>,"is_false_negative":<true|false>}
...
```

The ND-JSON file must be gzipped.

### Creating Labeling Functions (automatically)

To automatically create new labeling functions from a set of Gold Labels, run the 
following command-line:

```
java -Xms4g -Xmx8g com.computablefacts.morta.GuesstimateLabelingFunctions \
    -dry_run false \
    -language "<language>" \
    -label "<model_name>" \
    -gold_labels "/home/jdoe/my_gold_labels.json.gz" \
    -nb_candidates_to_consider 50 \
    -nb_labels_to_return 15 \
    -output_directory "/home/jdoe"
```

### Training a Generative Model

To train a Generative Model from a set of Labeling Functions, run the following 
command-line:

```
java -Xms4g -Xmx8g com.computablefacts.morta.TrainGenerativeModel \
    -dry_run false \
    -language "<language>" \
    -label "<model_name>" \
    -gold_labels "/home/jdoe/gold_labels_for_<model_name>.json.gz" \
    -labeling_functions "/home/jdoe/guesstimated_labeling_functions_for_<model_name>"_"<language>.xml.gz" \
    -output_directory "/home/jdoe"
```

### Training a Discriminative Model

To train a Discriminative Model from a Generative Model, run the following
command-line:

```
java -Xms4g -Xmx8g com.computablefacts.morta.TrainDiscriminativeModel \
    -dry_run false \
    -language "<language>" \
    -label "<model_name>" \
    -gold_labels "/home/jdoe/gold_labels_for_<model_name>.json.gz" \
    -label_model "/home/jdoe/label_model_for_<model_name>"_"<language>.xml.gz" \
    -alphabet "/home/jdoe/alphabet_for_<model_name>"_"<language>.xml.gz" \
    -output_directory "/home/jdoe" \
    -classifier "logit"
```

## Adding Morta to your build

Morta's Maven group ID is `com.computablefacts` and its artifact ID is `morta`.

To add a dependency on Morta using Maven, use the following:

```xml
<dependency>
  <groupId>com.computablefacts</groupId>
  <artifactId>morta</artifactId>
  <version>0.x</version>
</dependency>
```

## Snapshots 

Snapshots of Morta built from the `master` branch are available through Sonatype 
using the following dependency:

```xml
<dependency>
  <groupId>com.computablefacts</groupId>
  <artifactId>morta</artifactId>
  <version>0.x-SNAPSHOT</version>
</dependency>
```

In order to be able to download snapshots from Sonatype add the following profile 
to your project `pom.xml`:

```xml
 <profiles>
    <profile>
        <id>allow-snapshots</id>
        <activation><activeByDefault>true</activeByDefault></activation>
        <repositories>
            <repository>
                <id>snapshots-repo</id>
                <url>https://oss.sonatype.org/content/repositories/snapshots</url>
                <releases><enabled>false</enabled></releases>
                <snapshots><enabled>true</enabled></snapshots>
            </repository>
        </repositories>
    </profile>
</profiles>
```

## Publishing a new version

Deploy a release to Maven Central with these commands:

```bash
$ git tag <version_number>
$ git push origin <version_number>
```

To update and publish the next SNAPSHOT version, just change and push the version:

```bash
$ mvn versions:set -DnewVersion=<version_number>-SNAPSHOT
$ git commit -am "Update to version <version_number>-SNAPSHOT"
$ git push origin master
```