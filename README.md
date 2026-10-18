# Shelves

Application Android (Kotlin, Jetpack Compose, Room) pour ranger ses livres en étagères, suivre ses séries et savoir quels tomes on possède.

Sources de données : voir [docs/sources-de-donnees.md](docs/sources-de-donnees.md).

## Fonctionnalités

- **Étagères** : regroupements de livres créés par l'utilisateur (un livre peut être sur plusieurs étagères).
- **Ajout** : scan du code-barres EAN-13 / EAN-8 (scanner Google Play Services, sans permission caméra) ou saisie de l'ISBN.
- **Recherche** : BnF (notice, série, résumé), Google Books et Open Library (résumé, couverture), interrogées en parallèle.
- **Séries** : à l'ajout d'un tome, les autres tomes de la même édition sont récupérés à la BnF ; chaque série affiche « x / y tomes possédés » et se coche tome par tome.
- **Stockage local** : base Room ; sauvegarde automatique Android vers Google Drive, plus export/import JSON via le sélecteur de fichiers (Google Drive y figure).
- **Partage** : une étagère par SMS ou e-mail à un contact du téléphone, ou vers une autre application ; prêt d'un livre à un contact, et liste des prêts en cours.

## Organisation du code

```
app/src/main/java/fr/shelves/
  ShelvesApp.kt          conteneur de dépendances
  MainActivity.kt        navigation
  data/db/               entités et DAO Room
  data/remote/           BnF (SRU/UNIMARC), Open Library, Google Books, fusion
  data/BookRepository.kt logique métier (ajout, séries, prêts)
  data/BackupManager.kt  export/import JSON
  ui/…                   écrans Compose (étagères, étagère, ajout, livre, série, prêts, réglages)
```

## Ouvrir et compiler

1. Ouvrir le dossier dans Android Studio : il télécharge Gradle (`gradle/wrapper/gradle-wrapper.properties`) et le SDK.
2. Le script `gradlew` et son jar ne sont pas versionnés pour l'instant : les générer avec `gradle wrapper` (terminal d'Android Studio), puis les committer.
3. Lancer sur un appareil ou un émulateur **avec Google Play** (nécessaire au scanner ; sinon, saisir l'ISBN).
4. Tests unitaires : `./gradlew test`.

Le projet n'a pas encore été compilé : des corrections mineures sont probables au premier build.
