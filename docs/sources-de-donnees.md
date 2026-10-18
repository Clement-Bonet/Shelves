# Sources de données bibliographiques (gratuites)

Testées le 18/10/2026 avec l'ISBN 9782723488525 (One Piece, tome 1, Glénat).

| Source | Clé | Points forts | Limites |
|---|---|---|---|
| **BnF – SRU** `catalogue.bnf.fr/api/SRU` | non | Dépôt légal français, donc très complet pour l'édition française (manga, BD compris). Lien vers la série (UNIMARC 461 `$0` = n° de notice d'ensemble, `$v` = tome), collection (225), résumé (330, présent dans environ 86 % des notices de manga testées), auteurs, éditeur, pages. | Pas de couvertures. XML UNIMARC. Pas d'index sur le lien de série : on cherche par `bib.title` + `bib.publisher`, puis on filtre sur 461 `$0` (100 notices par page, paginé avec `startRecord`). Chaque édition d'une série a sa propre notice d'ensemble. Nouveau modèle de catalogage (Noemi, LRM) depuis juin 2026 : formats UNIMARC/Intermarc maintenus, mais le contenu peut évoluer. |
| **Open Library** | non | Couvertures par ISBN (`covers.openlibrary.org/b/isbn/<isbn>-M.jpg?default=false` → 404 si absente). Fiche JSON (`/api/books?jscmd=data`). | Base communautaire, peu fiable pour les éditions françaises ; pas de résumé dans cette API. |
| **Google Books** | facultative | Résumés et couvertures pour beaucoup de titres récents. | Sans clé, le quota anonyme partagé était épuisé lors du test (HTTP 429) : prévoir une clé gratuite (1 000 requêtes/jour), saisie dans les réglages de l'app. |
| Sudoc (ABES) | non | Catalogue des bibliothèques universitaires françaises, SRU/API JSON. | Recoupe la BnF ; moins de manga. |
| Wikidata (SPARQL) | non | Séries, nombre de volumes, liens entre éditions. | Couverture inégale par ISBN. |
| AniList (GraphQL) / MangaDex | non | Séries manga : nombre de volumes, couvertures, synopsis (souvent en anglais). | Pas de recherche par ISBN ; utile en complément pour une série déjà identifiée. |
| Hardcover (GraphQL) | jeton gratuit | Base moderne avec séries. | Surtout anglophone. |

Non retenues : ISBNdb (payant), Electre (payant, professionnel), Babelio, Bedetheque / BDGest, Manga-News (pas d'API publique).

## Mesure du 18/10/2026

46 livres parus en 2023, tirés du catalogue BnF (surtout mangas et BD : Glénat, Kana, Pika, Dargaud ; quelques romans : Gallimard, Folio, Albin Michel, Livre de poche).

| Source | Résumé | Couverture |
|---|---|---|
| BnF | 25 / 46 (54 %) | non fournie |
| Open Library | non fourni par l'API utilisée | 0 / 46 |
| Google Books (clé restreinte) | aucun résultat | aucun résultat |

Google Books renvoyait alors des erreurs « backendFailed » ou des listes vides, y compris pour des titres très connus : sa couverture des livres français n'a pas pu être évaluée.

**Choix pour Shelves :** la BnF en source principale (notice, série, résumé) ; Open Library pour la couverture quand elle existe ; Google Books seulement si l'utilisateur saisit sa propre clé. Aucune clé n'est intégrée à l'application. Le manque de couvertures est comblé par la **photo de la couverture** prise depuis la fiche du livre. Les sources sont interrogées en parallèle ; la BnF fait foi (`BookLookupService.merge`).

Sources : [Service SRU de la BnF](https://www.bnf.fr/fr/service-sru-catalogue-general-de-la-bnf), [api.bnf.fr](https://api.bnf.fr/fr/BnF-Catalogue-general), [Centre d'aide BnF (changement 2026)](https://www.bnf.fr/fr/centre-d-aide/recuperation-de-donnees), [Suivre les évolutions des données](https://www.bnf.fr/fr/suivre-les-evolutions-des-donnees), [Open Library API](https://openlibrary.org/developers/api), [Google Books API](https://developers.google.com/books/docs/v1/using), [comparatif d'API ISBN](https://www.vinzius.com/post/free-and-paid-api-isbn/).
