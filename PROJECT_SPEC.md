# PROJECT_SPEC.md

## Contexte

Mod Fabric pour Minecraft 26.2, inspiré de "RealTimeMod-Reborn" (NorthWestTreesGaming,
licence fermée — ce projet est une réécriture indépendante en Java pur, sous licence
permissive, pas un portage ni une décompilation de leur code).

Objectif : synchroniser l'heure du monde Minecraft sur l'heure réelle du serveur, la
phase de lune sur la vraie phase lunaire actuelle, et gérer une météo dynamique
crédible. Le projet est développé pour un usage personnel en priorité mais doit être
facilement repris et maintenu par n'importe qui.

## Contraintes techniques

- **Minecraft** : 26.2 uniquement pour cette première version.
- **Loader** : Fabric (Fabric Loader + Fabric API).
- **Java** : 25, et uniquement 25 (ni inférieur, ni Java 26). C'est la version LTS que Minecraft
  26.2 exige officiellement. Java 26 est une version non-LTS déjà en fin de support (sortie
  mars 2026, EOL septembre 2026) — cibler/compiler pour Java 26 casserait le mod sur tout
  serveur qui suit simplement l'exigence officielle de Mojang (Java 25), pour aucun bénéfice
  fonctionnel. `sourceCompatibility`/`targetCompatibility` (ou `toolchain.languageVersion`)
  doivent être fixés explicitement à 25 dans `build.gradle`, pas laissés à la version du JDK
  local du contributeur.
- **Mappings** : Mojang officielles (Mojmap), via `mappings loom.officialMojangMappings()`
  dans `build.gradle`. Ne pas utiliser Yarn.
- **Langage** : Java uniquement. Pas de Kotlin, pas de composant natif (Rust/JNI/JNA)
  — la charge de calcul est triviale et n'en justifie aucun.
- **Environnement de type** : server-side only. Aucun entrypoint client dans
  `fabric.mod.json`. Ne pas ajouter de dépendance client (pas de HUD, pas d'overlay).
  Doit fonctionner tel quel en solo (serveur intégré) sans configuration spéciale.
- **Mixins** : à éviter sauf si strictement impossible de faire autrement via l'API
  Fabric publique. Toute utilisation de mixin doit être justifiée en commentaire dans
  le code (pourquoi l'API publique ne suffit pas).

## Fonctionnalités

Le mod est composé de trois modules indépendants, chacun activable/désactivable
séparément dans la config, sans dépendance forte entre eux.

### 1. TimeSync
- Récupère l'heure système du serveur, la convertit en ticks Minecraft
  (1 jour = 24000 ticks) et l'applique au monde via l'API standard
  (`ServerWorld#setTimeOfDay` ou équivalent).
- Désactive `doDaylightCycle` pour éviter les conflits avec le cycle vanilla.
- Option de décalage horaire configurable (`offsetHours`), positif ou négatif.
- Option `syncAllWorlds` : synchroniser toutes les dimensions chargées ou seulement
  l'Overworld.

### 2. MoonSync
- Calcule la phase lunaire réelle actuelle via une formule astronomique standard
  (cycle synodique ~29.53 jours, à partir de la date julienne). Aucun appel réseau
  requis.
- Ajuste le compteur de jours du monde Minecraft pour que la phase en jeu
  (`jour_total % 8`) corresponde à la phase réelle calculée.
- Doit rester cohérent avec TimeSync (ne pas entrer en conflit sur le compteur de
  jours).

### 3. WeatherSync
- Pour cette v1 : système probabiliste "crédible" (transitions clair / pluie / orage
  via `ServerWorld#setWeather`), pas d'intégration météo réelle.
- Documenter clairement dans le code et le README que l'intégration d'une vraie API
  météo (ex. Open-Meteo, sans clé API requise) est un point d'extension future
  volontairement laissé de côté — pas une TODO bloquante, une porte ouverte pour un
  futur contributeur.

## Commandes admin

- `/timekeeper reload` — recharge la config sans redémarrer le serveur.
- `/timekeeper status` — affiche l'état courant (heure sync, phase de lune, module
  actifs/inactifs, dernière erreur éventuelle).
- `/timekeeper on` / `/timekeeper off` — active/désactive tous les modules à chaud.
  `off` doit remettre proprement `doDaylightCycle` (et `doWeatherCycle` si WeatherSync
  le désactive) à `true`, sans tenter de reconstituer une hypothétique heure "naturelle" —
  le cycle a été piloté artificiellement, il n'y a rien à restaurer, seulement à relâcher
  proprement. Persister l'état on/off dans la config.
- Permission : niveau opérateur (niveau 2 par défaut), configurable.

## Ce que le mod ne modifie jamais

Minecraft distingue deux compteurs persistés séparés et indépendants dans les données du
monde : `GameTime` (l'âge total du monde, utilisé par les statistiques et avancements,
augmente à chaque tick depuis la création) et `DayTime` (la valeur utilisée pour le cycle
jour/nuit et le calcul de la phase de lune, `(dayTime / 24000) % 8`). TimeSync et MoonSync
ne modifient **que** `DayTime`. `GameTime` (l'âge réel du serveur/monde) n'est jamais touché,
directement ou indirectement, par aucun module. Documenter cette distinction explicitement
dans le code (commentaire au point d'appel de `setTimeOfDay`) et dans le README — c'est une
confusion facile pour un futur contributeur.

## Ne laisser aucune trace après retrait du mod

Un mod ne peut exécuter aucun code après avoir été supprimé — limitation dure de tout mod
loader. La seule trace persistante que ce mod peut laisser dans le monde est la gamerule
`doDaylightCycle` (et `doWeatherCycle` le cas échéant) si elle reste désactivée. Deux
filets de sécurité, pas un seul :
1. `/timekeeper off` (voir ci-dessus) revient à l'état vanilla à la demande.
2. Un hook sur `ServerLifecycleEvents.SERVER_STOPPING` fait le même reset automatiquement à
   chaque arrêt normal du serveur, même si personne n'a pensé à lancer `off` avant de retirer
   le jar. Documenter dans le README que ce filet suppose un arrêt propre (pas un crash ou un
   `kill -9`) — c'est une limite honnête à énoncer plutôt qu'à cacher.

## Configuration

- Un seul fichier `config/timekeeper.properties`, plat, commenté ligne par ligne
  (pas de Cloth Config, pas d'AutoConfig — zéro dépendance de config externe).
- Rechargement à chaud pour la plupart des paramètres (voir `/timekeeper reload`).
- Champs minimum : `timeSyncEnabled`, `moonSyncEnabled`, `weatherSyncEnabled`,
  `offsetHours`, `syncAllWorlds`, `updateIntervalTicks`, `debugLogging`.

## Architecture attendue

- Package racine unique, sous-packages par module (`timesync`, `moonsync`,
  `weathersync`, `command`, `config`).
- Chaque module implémente une interface commune simple (ex. `SyncModule` avec
  `tick()`, `reload()`, `isEnabled()`) pour rester découplé et remplaçable
  individuellement.
- Pas de logique métier dans la classe d'entrée du mod — elle ne fait qu'initialiser
  les modules et enregistrer les commandes/événements.

## Distribution

- Publication sur **Modrinth uniquement** (pas CurseForge pour l'instant).
- Publication automatisée via **GitHub Actions** au tag de release
  (utiliser l'action officielle Modrinth si disponible, sinon leur API directement).
  Le workflow doit pouvoir tourner sans intervention manuelle une fois le tag poussé.

## Licence

MIT.

## Conventions de dépôt / branches

- `main` : toujours buildable, protégée (PR obligatoire, CI verte requise avant merge,
  pas de push direct).
- Une branche de ligne de version par cible Minecraft majeure quand le support
  multi-version arrivera (ex. `mc/26.2.x`), même si pour l'instant tout part de `main`.
- Tags Git pour chaque release (`v0.1.0`, etc.), alignés sur les releases Modrinth.

## CI/CD — automatisation complète

Objectif : boucle "commit conventionnel → merge → publication" sans étape manuelle entre
le merge d'une release et la disponibilité du jar sur Modrinth. Inspiré d'un pipeline
Rust existant (`cd.yml`/`release.yml` à motif decide/propose/publish), adapté et allégé
pour un mod Fabric — pas de matrice multi-OS pour le build (un jar Java est portable par
nature, contrairement à un binaire natif), pas de SBOM/attestations Sigstore
(disproportionné à cette échelle).

### `ci.yml` — vérification continue
- Déclenché sur chaque PR et chaque push sur `main`.
- `./gradlew build` (compile + lance les checks Gradle) sur `ubuntu-latest` uniquement.
- Aucune permission d'écriture (`permissions: contents: read`).
- Actions tierces épinglées par SHA de commit (pas par tag mutable), tag en commentaire à
  côté — mêmes raisons que le projet de référence : un tag est un pointeur mutable, un SHA
  ne l'est pas.

### `pr-hygiene.yml` — conventions
- Nom de branche : `<catégorie>/<slug>` (ex. `feature/moon-phase-sync`).
- Titre de PR en Conventional Commits (`feat: `, `fix: `, `chore: `, etc.) — c'est ce
  titre qui devient le message de commit sur `main` en squash merge, et c'est lui que
  `cd.yml` lit pour générer le changelog et décider du bump de version.

### `cd.yml` — décide et propose (jamais ne publie)
- Sur chaque push sur `main` : lit l'historique depuis le dernier tag `v*`, calcule le
  prochain numéro de version à partir des types de commits (`feat` → minor, `fix`/`perf`
  → patch, `!`/`BREAKING CHANGE:` → major), génère la section de changelog correspondante.
- Ouvre ou met à jour **une seule** PR de release (`chore(release): publish vX.Y.Z`) qui
  contient le bump de version + le changelog. Rien n'est publié tant que cette PR n'est
  pas mergée par une personne — c'est l'acte humain qui déclenche la suite.
- Au merge de cette PR précise : crée le tag `vX.Y.Z` et déclenche `release.yml`.

### `release.yml` — construit et publie
- Déclenché par le tag `v*`.
- Vérifie que le tag et la version du `build.gradle` concordent avant de continuer.
- `./gradlew build` sur `ubuntu-latest`, cible Java 25 explicitement.
- Crée une release GitHub en brouillon, y attache le jar, publie sur **Modrinth** via une
  action dédiée et épinglée par SHA (ex. `Kir-Antipov/mc-publish`, qui gère Modrinth +
  GitHub Releases en une passe), puis retire le statut brouillon une fois tout confirmé.
- Aucune étape ne dépend d'une intervention manuelle entre le merge de la PR de release et
  la disponibilité du jar sur Modrinth.

### `renovate.json` — dépendances à jour toutes seules
- Étend `config:recommended` + `:semanticCommits` + `helpers:pinGitHubActionDigests`.
- Une PR groupée hebdomadaire plutôt qu'une avalanche de PR individuelles.
- Auto-merge autorisé uniquement pour les mises à jour patch/minor de dépendances de
  développement ; toute mise à jour majeure ou de dépendance de production reste soumise
  à revue humaine.

### Branches protégées
- `main` : PR obligatoire, CI verte requise, pas de push direct — cohérent avec la
  convention de branches déjà actée.

## Documentation attendue

- `README.md` : présentation courte, install, config, architecture en quelques
  lignes (pas un roman — un futur mainteneur doit être opérationnel en 15 minutes
  de lecture).
- `CONTRIBUTING.md` : comment builder en local (`./gradlew build`), comment tester
  (checklist manuelle, voir plus bas), structure des modules.

## Tests

Pas de tests automatisés du comportement en jeu prévus pour cette v1 (pas
d'environnement de test automatisé simple pour un mod Fabric côté gameplay). À la
place : une checklist de vérification manuelle dans `CONTRIBUTING.md` que le
mainteneur suit après changement (heure qui avance correctement, phase de lune
cohérente sur plusieurs jours simulés, `/timekeeper reload` sans crash, comportement
en solo).

## Hors scope pour cette v1 (explicitement)

- Support d'autres versions Minecraft que 26.2.
- Support Forge/NeoForge/Quilt.
- Intégration météo réelle (API externe).
- Toute fonctionnalité client (HUD, overlay, config UI).
- Composant natif (Rust, JNI, JNA).
- Tests automatisés en jeu.

Ne pas ajouter ces éléments sans qu'ils soient explicitement redemandés, même si
l'implémentation semble triviale une fois le reste en place.

## Portée du travail attendu

Livrer un mod complet et fonctionnel pour ces trois modules, pas des stubs ni du
code "à finir plus tard" sur le périmètre défini ci-dessus. Pour toute décision
d'implémentation mineure non précisée ici (nom exact des méthodes, structure interne
d'un module), utiliser le meilleur jugement et avancer plutôt que de multiplier les
questions — mais signaler en une phrase si un choix structurant semble mal cadré par
ce document avant de continuer.
