<script setup lang="ts">
// Mise en page « journal » : quotidien contemporain, une et colonnes (ADR-0023, maquettes « La une du
// jour » et « L'article d'alerte »). Le premier élément reçu fait la une ; l'ordre n'est pas modifié.
// Couche mise en page : aucun appel réseau, aucun filtrage, aucun tri (AFF-02).
import '@fontsource/unifrakturmaguntia/latin-400.css'
import '@fontsource/playfair-display/latin-700.css'
import '@fontsource/playfair-display/latin-900.css'
import '@fontsource/source-serif-4/latin-400.css'
import '@fontsource/source-serif-4/latin-400-italic.css'
import '@fontsource/source-serif-4/latin-700.css'
import { computed, useId } from 'vue'
import MentionsObligatoires from '../../contenu/MentionsObligatoires.vue'
import { variablesCss } from '../contraste'
import type { ProprietesMiseEnPage } from '../modele'
import { cleRubrique, dateLongue, niveauSuivant, nombreElements, sommaire } from '../presentation'
import { niveauTitre } from '../selection'
import Titre from '../Titre.vue'
import { COULEURS } from './palette'

const props = defineProps<ProprietesMiseEnPage>()
const id = useId()
const jetons = variablesCss(COULEURS)
const niveau = computed(() => niveauTitre(props.niveauTitre))
const niveauElement = computed(() => niveauSuivant(niveau.value))
const rubriques = computed(() => sommaire(props.veille))
</script>

<template>
  <section class="journal" :style="jetons" :aria-labelledby="`${id}-titre`" data-mise-en-page="journal">
    <div class="feuille">
      <p class="edition">
        <span>
          Édition du
          <time v-if="veille.composeeLe" :datetime="veille.composeeLe">{{ dateLongue(veille.composeeLe) }}</time>
          <span v-else>{{ dateLongue(null) }}</span>
        </span>
        <span>Le quotidien de votre veille</span>
      </p>

      <header class="manchette">
        <Titre :id="`${id}-titre`" :niveau="niveau" class="titre">Argos</Titre>
        <p class="devise">Observer. Corréler. Comprendre. Anticiper.</p>
      </header>

      <p v-if="veille.exemple" class="exemple" data-exemple>Données d’exemple : ces éléments sont fictifs.</p>

      <section class="sommaire" :aria-labelledby="`${id}-rubriques`">
        <Titre :id="`${id}-rubriques`" :niveau="niveauElement" class="sommaire-titre">Rubriques</Titre>
        <ul role="list" class="sommaire-liste">
          <li v-for="r in rubriques" :key="cleRubrique(r.rubrique)" data-rubrique>
            {{ r.rubrique.libelle }} <span class="compte">({{ nombreElements(r.nombre) }})</span>
          </li>
        </ul>
      </section>

      <ul role="list" class="colonnes">
        <li
          v-for="(element, index) in veille.elements"
          :key="element.identifiant"
          :class="index === 0 ? 'une' : 'article'"
        >
          <article :aria-labelledby="`${id}-e${index}`" data-element>
            <p class="surtitre">
              <span data-champ="type">{{ element.type.libelle }}</span>
              <template v-if="element.gravite">
                <span aria-hidden="true"> · </span>
                <span>gravité <span data-champ="gravite">{{ element.gravite.libelle }}</span></span>
              </template>
            </p>
            <Titre :id="`${id}-e${index}`" :niveau="niveauElement" class="article-titre" data-champ="titre">{{
              element.titre
            }}</Titre>
            <p v-if="element.resume" class="chapo" data-champ="resume">{{ element.resume }}</p>
            <div class="projets">
              <span class="libelle">Projets concernés :</span>
              <ul v-if="element.projets.length" role="list" class="projets-liste">
                <li v-for="(p, ip) in element.projets" :key="ip">
                  <span data-champ="projet">{{ p.nom }}</span
                  ><template v-if="p.niveau"> (<span data-champ="niveau">{{ p.niveau.libelle }}</span>)</template>
                </li>
              </ul>
              <span v-else data-champ="sans-projet">aucun projet visible</span>
            </div>
            <p v-if="element.action" class="action">
              <strong>À faire :</strong> <span data-champ="action">{{ element.action }}</span>
            </p>
            <p v-if="element.rubriques.length" class="rubriques">
              <span class="libelle">Rubriques :</span>{{ ' ' }}
              <template v-for="(r, i) in element.rubriques" :key="cleRubrique(r)">
                <span v-if="i > 0" aria-hidden="true"> · </span>
                <span data-champ="rubrique">{{ r.libelle }}</span>
              </template>
            </p>
            <MentionsObligatoires :mentions="element.mentions" />
          </article>
        </li>
      </ul>

      <footer class="pied">
        <span>Seule la forme change d’une mise en page à l’autre : informations, sources et mentions restent les mêmes.</span>
      </footer>
    </div>
  </section>
</template>

<style scoped>
.journal {
  container-type: inline-size;
  background: var(--table);
  padding: 0.75rem;
  font-family: 'Source Serif 4', Georgia, serif;
  color: var(--encre);
  line-height: 1.5;
}
.feuille {
  background: var(--papier);
  padding: 1rem clamp(0.75rem, 4cqi, 2.5rem) 1.5rem;
  box-shadow: 0 2px 12px var(--ombre);
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}
.edition {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.25rem 1rem;
  margin: 0;
  font-size: 0.8rem;
  letter-spacing: 0.04em;
  color: var(--gris);
}
.manchette {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  border-top: 3px solid var(--encre);
  border-bottom: 3px double var(--encre);
  padding: 0.75rem 0;
  text-align: center;
}
.titre {
  margin: 0;
  font-family: 'UnifrakturMaguntia', 'Playfair Display', serif;
  font-weight: 400;
  font-size: clamp(3rem, 14cqi, 6rem);
  line-height: 1;
}
.devise {
  margin: 0;
  font-style: italic;
  color: var(--gris);
}
.exemple {
  margin: 0;
  padding: 0.4rem 0.75rem;
  background: var(--encart);
  color: var(--rouge);
  font-weight: 700;
  text-align: center;
}
.sommaire {
  background: var(--encart);
  padding: 0.6rem 0.9rem;
}
.sommaire-titre {
  margin: 0 0 0.3rem;
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}
.sommaire-liste {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 1.5rem;
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
.compte {
  text-transform: none;
  letter-spacing: 0;
  color: var(--gris);
}
.colonnes {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 15rem), 1fr));
  gap: 1.25rem 1.75rem;
}
.une {
  grid-column: 1 / -1;
  padding-bottom: 1rem;
  border-bottom: 3px double var(--encre);
}
.article {
  border-top: 1px solid var(--filet);
  padding-top: 0.75rem;
}
article {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.surtitre {
  margin: 0;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  color: var(--gris);
}
.une .surtitre {
  color: var(--rouge);
}
.article-titre {
  margin: 0;
  font-family: 'Playfair Display', Georgia, serif;
  font-weight: 700;
  font-size: 1.4rem;
  line-height: 1.15;
  overflow-wrap: anywhere;
}
.une .article-titre {
  font-weight: 900;
  font-size: clamp(1.75rem, 6cqi, 3.25rem);
  line-height: 1.05;
}
.chapo {
  margin: 0;
  color: var(--encreDouce);
}
.une .chapo {
  font-size: 1.2rem;
  font-style: italic;
}
.projets,
.rubriques,
.action {
  margin: 0;
  font-size: 0.95rem;
  color: var(--encreDouce);
}
.projets {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 0.5rem;
}
.projets-liste {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 0.75rem;
  margin: 0;
  padding: 0;
  list-style: none;
}
.libelle {
  font-weight: 700;
  color: var(--encre);
}
article :deep(.mentions) {
  font-size: 0.8rem;
  color: var(--discret);
  border-top: 1px solid var(--filet);
  padding-top: 0.35rem;
}
article :deep(.mentions a) {
  color: var(--rouge);
  font-weight: 700;
}
.journal :deep(a:focus-visible) {
  outline: 3px solid var(--focus);
  outline-offset: 2px;
}
.pied {
  border-top: 3px solid var(--encre);
  padding-top: 0.5rem;
  font-size: 0.8rem;
  color: var(--gris);
}
</style>
