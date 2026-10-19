# Shelves

Application Android (Kotlin, Jetpack Compose, Room) pour ranger ses livres en étagères, suivre ses séries et savoir quels tomes on possède.

Sources de données : voir [docs/sources-de-donnees.md](docs/sources-de-donnees.md).

## Fonctionnalités

- **Étagères** : regroupements de livres créés par l'utilisateur (un livre peut être sur plusieurs étagères). Une étagère virtuelle « Non rangés » réunit les livres possédés qui ne sont dans aucune étagère ; elle n'apparaît que s'il y en a. Recherche (titre, auteur, ISBN, série) et tri (titre, auteur, ajout le plus récent) dans chaque étagère.
- **Ajout** : scan du code-barres EAN-13 / EAN-8 (scanner Google Play Services, sans permission caméra) ou saisie de l'ISBN.
- **Recherche** : BnF (notice, série, résumé) et Open Library (couverture), interrogées en parallèle ; Google Books en plus si l'utilisateur saisit sa propre clé. Sans réseau, ou si la BnF ne répond pas, l'application le dit au lieu d'annoncer un livre inconnu.
- **Couverture** : photo prise depuis la fiche du livre avec le scanner de documents de Google, qui détecte les bords du livre, recadre et redresse la perspective (import depuis la galerie possible) ; « Photo sans recadrage » dans le menu sert de secours. Aucune permission caméra demandée ; image réduite et stockée sur le téléphone.
- **Séries** : à l'ajout d'un tome, les autres tomes de la même édition sont récupérés à la BnF ; chaque série affiche « x / y tomes possédés » et se coche tome par tome.
- **Actions groupées sur une série** (menu ⋮ de la série) : ranger ou déplacer toute la série dans une étagère (existante ou nouvelle), y ajouter des livres qui n'avaient pas de série, la supprimer avec tous ses tomes. Un appui long sur un tome active la sélection multiple : ranger dans une étagère, changer de série (par exemple séparer les « classiques » Kana en Goldorak, Saint Seiya…), supprimer. Un tome supprimé n'est plus rajouté par les synchronisations (« Réafficher les tomes supprimés » dans le menu de la série).
- **Modification d'une fiche** (crayon sur la fiche du livre) : titre, sous-titre, auteurs, tome, pages, éditeur, date, ISBN (vérifié, et refusé s'il appartient déjà à un autre livre) et résumé. « Actualiser depuis les sources » ne remplit ensuite que les champs vides.
- **Corrections manuelles** : depuis la fiche d'un livre, choisir sa série (existante, nouvelle ou aucune) et son tome ; depuis une série, la renommer ou la fusionner dans une autre (utile quand la BnF répartit une même série sur plusieurs notices). Les choix manuels ne sont jamais écrasés par une synchronisation, et une série fusionnée continue d'apporter ses tomes BnF à la série qui l'a reçue.
- **Stockage local** : base Room ; sauvegarde automatique Android vers Google Drive, plus export/import d'une archive ZIP (données + photos) via le sélecteur de fichiers (Google Drive y figure).
- **Partage** : une étagère par SMS ou e-mail à un contact du téléphone, ou vers une autre application ; prêt d'un livre à un contact, et liste des prêts en cours.

## Organisation du code

```
app/src/main/java/com/katachilabs/shelves/
  ShelvesApp.kt          conteneur de dépendances
  MainActivity.kt        navigation
  data/db/               entités et DAO Room
  data/remote/           BnF (SRU/UNIMARC), Open Library, Google Books, fusion
  data/BookRepository.kt logique métier (ajout, séries, prêts)
  data/BackupManager.kt  export/import ZIP (données + photos)
  ui/…                   écrans Compose (étagères, étagère, ajout, livre, série, prêts, réglages)
```

## Ouvrir et compiler

Outils : Gradle 9.8, Android Gradle Plugin 9.4 (Kotlin intégré), Kotlin 2.4, compileSdk 37.
N'importe quel JDK 17 ou plus récent convient, y compris le Java 25 fourni avec Android Studio.

1. Ouvrir le dossier dans Android Studio.
2. Compiler : `./gradlew assembleDebug` ; tests unitaires : `./gradlew test`.
3. Lancer sur un appareil ou un émulateur **avec Google Play** (nécessaire au scanner ; sinon, saisir l'ISBN).

## Publier

1. Copier `keystore.properties.example` en `keystore.properties` (jamais versionné) et y indiquer la clé de publication.
2. `./gradlew bundleRelease` produit `app/build/outputs/bundle/release/app-release.aab`, signé, à envoyer dans la console Google Play.
3. Avant chaque version, augmenter `versionCode` (et `versionName`) dans `app/build.gradle.kts`.

Textes et réponses pour la console : [fiche du Store](docs/play-store/fiche.md), [sécurité des données](docs/play-store/securite-des-donnees.md),
[politique de confidentialité](docs/politique-de-confidentialite.md) (lien aussi affiché dans les réglages de l'application).

## Clé Google Books

Facultative, à saisir dans les réglages de l'application ; aucune clé n'est intégrée à l'application,
et sans clé Google Books n'est pas interrogé. Dans la console Google Cloud, restreindre la clé :
- **API** : Books API uniquement ;
- **Applications Android** : package `com.katachilabs.shelves` et empreinte SHA-1 du certificat de signature.
  - Debug : `keytool -J-Duser.language=en -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android`
    (propre à chaque poste ; l'option `-J-Duser.language=en` contourne un bug d'affichage de keytool en français).
  - Release : ajouter aussi l'empreinte de la clé de publication (ou celle de Google Play App Signing) le moment venu.

L'application envoie elle-même les en-têtes `X-Android-Package` et `X-Android-Cert` exigés par cette restriction.

## Licence

Code sous licence [MIT](LICENSE). Les données récupérées restent soumises aux conditions de leurs sources : catalogue de la BnF (licence ouverte Etalab, mention de la source), Google Books et Open Library.
