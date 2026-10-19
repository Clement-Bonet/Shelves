# Politique de confidentialité de Shelves

*Dernière mise à jour : 19 octobre 2026*

Shelves est une application Android de gestion de bibliothèque personnelle, éditée par Clément Bonet (Katachi Labs). Cette page explique quelles données l'application utilise, où elles sont conservées et avec qui elles sont échangées.

**En résumé : Shelves n'a ni compte utilisateur, ni publicité, ni outil de mesure d'audience. Vos données restent sur votre téléphone. Seuls les codes ISBN des livres sont envoyés à des catalogues publics pour en retrouver la fiche.**

## Données conservées sur le téléphone

L'application enregistre, uniquement sur votre appareil :

- vos étagères, vos livres, vos séries, les tomes possédés et vos notes ;
- les photos de couverture que vous prenez ;
- pour un prêt : le nom du contact choisi et une référence vers sa fiche dans vos contacts ;
- la clé Google Books, si vous en saisissez une.

Ces données ne sont jamais transmises au développeur. Elles sont supprimées si vous désinstallez l'application ou effacez ses données dans les réglages d'Android.

## Données envoyées à des services extérieurs

Pour retrouver la fiche d'un livre, l'application envoie **le code ISBN ou EAN** du livre (et, pour une série, son titre et son éditeur) à :

- **la Bibliothèque nationale de France** (catalogue.bnf.fr) ;
- **Open Library** (openlibrary.org), pour les couvertures ;
- **Google Books** (googleapis.com), seulement si vous avez saisi votre propre clé. L'application transmet alors aussi son nom de package et l'empreinte de son certificat, comme l'exige Google pour les clés restreintes.

Aucune donnée personnelle n'est jointe à ces requêtes. Comme pour toute connexion à Internet, ces services voient l'adresse IP de votre appareil. Ils appliquent leurs propres politiques de confidentialité.

## Appareil photo et scanners

Le scan des codes-barres et le recadrage des photos de couverture sont réalisés par les **services Google Play** (ML Kit), sur votre appareil. L'application ne demande pas l'accès à la caméra : elle reçoit seulement le code lu ou l'image recadrée. Une photo prise sans recadrage passe par votre application d'appareil photo habituelle.

## Contacts

Shelves ne lit pas votre carnet d'adresses. Quand vous choisissez un contact (pour un prêt, ou pour partager une étagère par SMS ou par e-mail), Android ne lui donne accès qu'au contact choisi. Le numéro ou l'adresse e-mail sert uniquement à ouvrir votre application de messagerie ; il n'est pas enregistré.

## Sauvegardes

- **Sauvegarde automatique Android** : si elle est activée sur votre téléphone, Android sauvegarde les données de l'application dans votre compte Google, comme pour les autres applications.
- **Export manuel** : le fichier de sauvegarde est enregistré à l'endroit que vous choisissez (téléphone, Google Drive…). Il contient vos données et vos photos.

## Ce que Shelves ne fait pas

- pas de compte ni de connexion ;
- pas de publicité ;
- pas de mesure d'audience, ni de suivi ;
- pas de vente ni de partage de données.

## Enfants

L'application ne s'adresse pas spécifiquement aux enfants et ne collecte aucune donnée les concernant.

## Contact

Pour toute question sur cette politique : **[adresse e-mail de contact]**.

Toute modification de cette politique sera publiée sur cette page, avec sa date de mise à jour.
