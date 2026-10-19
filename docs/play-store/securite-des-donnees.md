# Formulaire « Sécurité des données » (console Google Play)

Réponses à donner dans Contenu de l'application > Sécurité des données, cohérentes avec la [politique de confidentialité](../politique-de-confidentialite.md).

Rappel de la définition de Google : une donnée est **collectée** si elle quitte l'appareil vers le développeur ou un tiers. Des données traitées uniquement sur l'appareil ne sont pas « collectées ».

## Collecte et partage

| Question | Réponse | Pourquoi |
|---|---|---|
| L'application collecte-t-elle ou partage-t-elle des types de données utilisateur obligatoires ? | **Non** | Bibliothèque, photos, notes et contacts de prêt restent sur l'appareil. Les codes ISBN envoyés à la BnF, Open Library et Google Books ne sont pas des données utilisateur au sens de Google (ce sont des identifiants publics de livres, sans lien avec la personne). |
| Toutes les données sont-elles chiffrées en transit ? | **Oui** | Toutes les requêtes passent en HTTPS. |
| Les utilisateurs peuvent-ils demander la suppression de leurs données ? | **Oui** | Suppression dans l'application, effacement des données ou désinstallation. Aucune donnée n'est conservée ailleurs. |

## Points d'attention

- **Contacts** : le sélecteur de contacts d'Android ne donne accès qu'au contact choisi, et rien ne quitte l'appareil. Ce n'est donc pas une collecte. Si Google pose la question lors de l'examen, la réponse est : « nom du contact enregistré localement pour suivre un prêt, jamais transmis ».
- **Sauvegarde automatique Android** : elle est gérée par Android dans le compte Google de l'utilisateur, et ne compte pas comme une collecte par l'application.
- **ML Kit (scan et recadrage)** : traitement sur l'appareil par les services Google Play. Google indique que ces API n'envoient pas les images ; à vérifier dans sa documentation au moment de la déclaration, au cas où elle aurait évolué.
- **Google Books** : utilisé seulement avec la clé de l'utilisateur ; seule la recherche par ISBN est envoyée.
