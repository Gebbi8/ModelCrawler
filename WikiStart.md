sample config 
--------------

can be created using 

```
java -cp ~/unisonSyncPrivate/education/dev/modelcrawler/ModelCrawler/target/ModelCrawler-0.0.4-jar-with-dependencies.jar de.unirostock.sems.ModelCrawler.App --template
```

example:

```json
{
  "workingDir" : "/home/martin/zzzZZZzzz/muell/modelcrawler",
  "encoding" : "UTF-8",
  "pathSeparator" : "/", 
  "extensionBlacklist" : [ "png", "bmp", "jpg", "jpeg", "html", "xhtml", "svg", "pdf", "json", "pl", "rdf", "rar", "msh", "zip", "htm" ],
  "tempDirPrefix" : "ModelCrawler",
  "workingDirConfig" : "config.json",
  "urnNamespace" : "model",
  "morreUrl" : "http://localhost:7474/morre/",
  "databases" : [ {
    "type" : "BMDB",
    "enabled" : true,
    "workingDir" : "wd-biomodels",
    "limit" : -1, 
    "ftpUrl" : "ftp://ftp.ebi.ac.uk/pub/databases/biomodels/releases/"
  }, { 
    "type" : "PMR2",
    "enabled" : true,
    "workingDir" : "wd-cellml",
    "limit" : -1, 
    "hashAlgo" : "MD5",
    "repoListUrl" : "http://models.cellml.org/workspace_list_txt"
  } ], 
  "storage" : {
    "type" : "file",
    "httpAccessPath" : "http://localhost/models/",
    "baseDir" : "/home/martin/zzzZZZzzz/muell/modelcrawler-storage"
  }
}
```

run 
----

```
java -jar target/ModelCrawler-0.0.4-jar-with-dependencies.jar -c /tmp/modelcrawler.config --test
```

```
-c               Path to config
--config
--template       Writes down a template config file (overrides existing config!)
--test           Test mode. Nothing is pushed to morre nor stored persistent
--no-morre       Do not utilize morre to determine the latest known version nor
                 stores any model in the database. Just download and store models.
                 May cause doubles, when used for BioModels
```

IDs 
----

Only the combination of fileId and versionId is unique.

### fileId 

```
urn:model:models.cellml.org:workspace:19f:!:chloride-ion:model.xml
```
The fileId represented as an urn is separated by an **!** into 2 parts: The first one specifies the path to the workspace and the second one the path within the workspace. To link to a specific version, it is possible to add the versionId behind the **!** or ship it as a second field.

### versionId 

Either the commit hash (PMR2) or the date of the release (BMDB).

info.js 
--------

Every FileBasedStorage creates an `info.json` with information about crawled versions and models located in the base dir of a workspace. It consists of a map of models, linking the fileId to information about the available versions and the time of the version as UNIX Timestamp.

```
{
  "models" : {
    "urn:model:models.cellml.org:workspace:178:!:pHcontrol.cellml" : {
      "fileId" : "urn:model:models.cellml.org:workspace:178:!:pHcontrol.cellml",
      "versions" : {
        "3de98843f02c6e130b6c607fdb35e2edca2a57e6" : 1373873463,
        "030ab6f6440c2795b9c7b0115f73ca1888f008d4" : 1373873596,
        "570f21971b7fca13184af0019fce2b3c677b1266" : 1373873980,
        "e1501746c35cafc2cfc25f935e022ef65fe8a492" : 1378798047,
        "2320d7dee569a63f5be4e47ea0b81fe1eaa0c151" : 1378864165,
        "88933857fe4e38c4de28aae14a7140c602d3b19a" : 1379024194,
        "bce14624c36bb41f33dea5b2ae7e725be9cd3cfe" : 1379024227,
        "731411e3536157b0905b93c3ba526dde603b4b20" : 1379028871,
        "2b59edd002f0721d39e738789ddbbe9529b4de3e" : 1379049449
      }
    },
    "urn:model:models.cellml.org:workspace:178:!:PressureConversion.cellml" : {
      "fileId" : "urn:model:models.cellml.org:workspace:178:!:PressureConversion.cellml",
      "versions" : {
        "2320d7dee569a63f5be4e47ea0b81fe1eaa0c151" : 1378864165,
        "a8e77a8940455c1250de09e50fdb1ab6bffb85d2" : 1378875142,
        "f005ef715944cfc7f4035a7a7d5cad7f8a1da2a5" : 1378877434,
        "731411e3536157b0905b93c3ba526dde603b4b20" : 1379028871
      }
    },
    "urn:model:models.cellml.org:workspace:178:!:PhysicalConstants.cellml" : {
      "fileId" : "urn:model:models.cellml.org:workspace:178:!:PhysicalConstants.cellml",
      "versions" : {
        "a8e77a8940455c1250de09e50fdb1ab6bffb85d2" : 1378875142
      }
    }
  }
}
```

Starting Points 
----------------
 * /TracGuide --  Built-in Documentation
 * [The Trac project](http://trac.edgewall.org/) -- Trac Open Source Project
 * [Trac FAQ](http://trac.edgewall.org/wiki///TracFaq) -- Frequently Asked Questions
 * /TracSupport --  Trac Support

For a complete list of local wiki pages, see /TitleIndex.
