Warum Docker?

Es gibt verschiedene Gründe warum man openems als Dockercontainer betrieben sollte. Einige davon sind:

- Einfaches testen von unterscheidlichen Versionen
- Ausführen von mehreren Instanzen
- Isolieren von openems vom Rest des Systems
- Orchestierung und Automatiersung bei der bereitstellung von openems

Zum starten von openems kann der jeweilige Container per docker run gestartet werden oder auf ein docker compose file zurückgegriffen werden.




Erstellen der Container oder der openems Datein mit docker build.

Das Dockerfile kann sowohl ui, edge und backend sowohl als Container als auch als jar-File erstellen und diese exportieren.

Der Befehl zum erstellen eines Container ist:
docker build --targe=<APP> .

Um einen Container zu erstellen muss das passenden stage target (<APP>) für den zu erstellenden Container angegeben werden.

App	target
ui	ui_container
edge	edge_container
backend	backend_container


Der Befehl zum erstellen und exportieren der openems Datein ist:
docker build --target=<APP> --export build .

Um einer der openems Datei zu erstellen muss auch hier ein stage target(<APP>) angegben werden.

App	target
ui	ui_export
edge	edge_export
backend	backend_export

Im die Datein werden in den Ordner build exportiert.

Zusätzlich können beim Erstellen von Datein oder Container bestimmte Build Arguments übergeben werden, die den Buildvorgang beeinflussen.

SOURCE=<git/local>		Gibt an ob die Sourcedatein die für den Buildvorgang genutzt werden sollen aus git oder local übernommen werden sollen
BRANCH=<branchname>		Gibt den Branch an aus dem die Sourcedatein übernommen werden sollen, gilt nur bei SOURCE=git
JAVA_VERSION=<openjdkxx>	Gibt an mit welcher Version von Java JDK die Sourcedatein gebaut werden sollen und mit welcher Version von Java JRE die Datein im Container ausgeführt werden sollen 
UI_MODE=prod			Gibt beim Erstellen der ui Datein oder Containers an, ob diese für produktiven oder debug Einsatz erstellt werden
UI_VERSION=edge			Gibt beim Erstellen der ui Datien oder Containers an, ob diese für edge oder backend erstellt werden
