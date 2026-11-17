<script setup lang="ts">
// Mise en page « gazette » : presse imprimée du XIXe siècle (ADR-0023, maquette « La une de la
// gazette ») : gothique, caractères en bois, filets, colonnes. L'humour est dans la forme, jamais
// dans les faits : gravures et bandeaux sont décoratifs (aria-hidden), les informations sont celles
// de l'élément. Le premier élément reçu fait la une ; l'ordre n'est pas modifié.
// Couche mise en page : aucun appel réseau, aucun filtrage, aucun tri (AFF-02).
import '@fontsource/unifrakturcook/latin-700.css'
import '@fontsource/alfa-slab-one/latin-400.css'
import '@fontsource/playfair-display-sc/latin-400.css'
import '@fontsource/playfair-display-sc/latin-700.css'
import '@fontsource/im-fell-english/latin-400.css'
import '@fontsource/im-fell-english/latin-400-italic.css'
import { computed, useId } from 'vue'
import MentionsObligatoires from '../../contenu/MentionsObligatoires.vue'
import { variablesCss } from '../contraste'
import type { CodeNature, ProprietesMiseEnPage } from '../modele'
import { cleRubrique, dateLongue, niveauSuivant, nombreElements, sommaire } from '../presentation'
import { bornerNiveauTitre } from '../selection'
import Titre from '../Titre.vue'
import { COULEURS } from './palette'

const props = defineProps<ProprietesMiseEnPage>()
const id = useId()
const jetons = variablesCss(COULEURS)
const niveau = computed(() => bornerNiveauTitre(props.niveauTitre))
const niveauElement = computed(() => niveauSuivant(niveau.value))
const rubriques = computed(() => sommaire(props.veille))

/** Bandeau décoratif d'époque de chaque nature, affiché au-dessus de la nature réelle. */
const BANDEAUX: Readonly<Record<CodeNature, string>> = {
  'avis-securite': 'Avis de recherche',
  'fiche-reglementaire': 'Le coin des décrets',
  'nouvelle-version': 'Nouveautés',
  'fin-de-support': 'Avis de décès',
  echeance: 'Calendrier',
  actualite: 'Échos',
}
</script>

<template>
  <section class="gazette" :style="jetons" :aria-labelledby="`${id}-titre`" data-mise-en-page="gazette">
    <div class="feuille">
      <header class="manchette">
        <span class="surmanchette">Gazette quotidienne de la veille technique</span>
        <div class="titre-ligne">
          <svg class="oeil" viewBox="0 0 70 70" aria-hidden="true" focusable="false">
            <circle cx="35" cy="35" r="32" class="trait" />
            <circle cx="35" cy="35" r="27" class="trait fin" />
            <path d="M12 35 C22 20, 48 20, 58 35 C48 50, 22 50, 12 35 Z" class="blanc" />
            <circle cx="35" cy="35" r="8" class="plein" />
          </svg>
          <Titre :id="`${id}-titre`" :niveau="niveau" class="titre">Argos</Titre>
          <svg class="oeil" viewBox="0 0 70 70" aria-hidden="true" focusable="false">
            <circle cx="35" cy="35" r="32" class="trait" />
            <circle cx="35" cy="35" r="27" class="trait fin" />
            <path d="M12 35 C22 20, 48 20, 58 35 C48 50, 22 50, 12 35 Z" class="blanc" />
            <circle cx="35" cy="35" r="8" class="plein" />
          </svg>
        </div>
      </header>

      <p class="filet-edition">
        <span>
          Édition du
          <time v-if="veille.composeeLe" :datetime="veille.composeeLe">{{ dateLongue(veille.composeeLe) }}</time>
          <span v-else>{{ dateLongue(null) }}</span>
        </span>
        <span class="devise">Observer · Corréler · Comprendre · Anticiper</span>
      </p>

      <p v-if="veille.exemple" class="exemple" data-exemple>Données d’exemple : ces éléments sont fictifs.</p>

      <section class="sommaire" :aria-labelledby="`${id}-rubriques`">
        <Titre :id="`${id}-rubriques`" :niveau="niveauElement" class="sommaire-titre">Dans ce numéro</Titre>
        <ul role="list" class="sommaire-liste">
          <li v-for="r in rubriques" :key="cleRubrique(r.rubrique)" data-rubrique>
            <span>{{ r.rubrique.libelle }}</span><span class="points" aria-hidden="true" /><span>{{
              nombreElements(r.nombre)
            }}</span>
          </li>
        </ul>
      </section>

      <ul role="list" class="colonnes">
        <li
          v-for="(element, index) in veille.elements"
          :key="element.identifiant"
          :class="index === 0 ? 'une' : 'colonne'"
        >
          <article :aria-labelledby="`${id}-e${index}`" data-element>
            <p class="bandeau">
              <span class="bandeau-epoque" aria-hidden="true">{{ BANDEAUX[element.type.code] ?? 'Échos' }}</span>
              <span class="nature" data-champ="type">{{ element.type.libelle }}</span>
              <span v-if="element.gravite" class="gravite"
                >Gravité <span data-champ="gravite">{{ element.gravite.libelle }}</span></span
              >
            </p>
            <Titre :id="`${id}-e${index}`" :niveau="niveauElement" class="article-titre" data-champ="titre">{{
              element.titre
            }}</Titre>
            <div class="texte">
              <p v-if="element.resume" class="resume" data-champ="resume">{{ element.resume }}</p>
              <div class="signalement">
                <p class="signalement-titre" aria-hidden="true">Signalement</p>
                <div class="projets">
                  <span class="libelle">Aperçu chez :</span>
                  <ul v-if="element.projets.length" role="list" class="projets-liste">
                    <li v-for="(p, ip) in element.projets" :key="ip">
                      <span data-champ="projet">{{ p.nom }}</span
                      ><template v-if="p.niveau"> (<span data-champ="niveau">{{ p.niveau.libelle }}</span>)</template>
                    </li>
                  </ul>
                  <span v-else data-champ="sans-projet">aucun projet visible</span>
                </div>
                <p v-if="element.action" class="action">
                  <strong>Le remède :</strong> <span data-champ="action">{{ element.action }}</span>
                </p>
              </div>
            </div>
            <p v-if="element.rubriques.length" class="rubriques">
              <span class="libelle">Rubriques :</span>{{ ' ' }}
              <template v-for="(r, i) in element.rubriques" :key="cleRubrique(r)">
                <span v-if="i > 0" aria-hidden="true"> — </span>
                <span data-champ="rubrique">{{ r.libelle }}</span>
              </template>
            </p>
            <MentionsObligatoires :mentions="element.mentions" />
          </article>
        </li>
      </ul>

      <footer class="pied">
        L’humour est dans la forme, jamais dans les faits : informations, sources et mentions restent exactes.
      </footer>
    </div>
  </section>
</template>

<style scoped>
.gazette {
  container-type: inline-size;
  background: var(--table);
  padding: 0.9rem;
  font-family: 'IM Fell English', Georgia, serif;
  color: var(--encre);
  line-height: 1.5;
}
.feuille {
  background: var(--papier);
  box-shadow: 0 6px 24px var(--ombre);
  padding: 1rem clamp(0.75rem, 4cqi, 2.5rem) 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}
.manchette {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.15rem;
  text-align: center;
}
.surmanchette {
  font-family: 'Playfair Display SC', serif;
  font-size: 0.9rem;
  letter-spacing: 0.25em;
}
.titre-ligne {
  display: flex;
  align-items: center;
  gap: clamp(0.5rem, 3cqi, 1.5rem);
}
.titre {
  margin: 0;
  font-family: 'UnifrakturCook', serif;
  font-weight: 700;
  font-size: clamp(3rem, 15cqi, 7rem);
  line-height: 0.95;
}
.oeil {
  width: clamp(2rem, 7cqi, 4.4rem);
  height: auto;
  flex: 0 0 auto;
}
.trait {
  fill: none;
  stroke: var(--encre);
  stroke-width: 2;
}
.trait.fin {
  stroke-width: 1;
}
.blanc {
  fill: var(--papierClair);
  stroke: var(--encre);
  stroke-width: 2.5;
}
.plein {
  fill: var(--encre);
}
.filet-edition {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.25rem 1rem;
  margin: 0;
  padding: 0.3rem 0;
  border-top: 5px solid var(--encre);
  border-bottom: 1px solid var(--encre);
  font-family: 'Playfair Display SC', serif;
  font-size: 0.85rem;
  letter-spacing: 0.06em;
}
.devise {
  letter-spacing: 0.2em;
}
.exemple {
  align-self: center;
  margin: 0;
  padding: 0.25rem 0.9rem;
  background: var(--encre);
  color: var(--papier);
  font-family: 'Alfa Slab One', serif;
  letter-spacing: 0.08em;
}
.sommaire {
  align-self: center;
  width: min(100%, 26rem);
  border: 3px double var(--encre);
  background: var(--papierClair);
  padding: 0.5rem 0.8rem;
}
.sommaire-titre {
  margin: 0 0 0.25rem;
  font-family: 'Playfair Display SC', serif;
  font-weight: 700;
  font-size: 1rem;
  text-align: center;
  border-bottom: 1px solid var(--encre);
}
.sommaire-liste {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 0.95rem;
}
.sommaire-liste li {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
}
.points {
  flex: 1;
  border-bottom: 1px dotted var(--encre);
}
.colonnes {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 14rem), 1fr));
  gap: 1.25rem 0;
  border-top: 3px double var(--encre);
  padding-top: 0.75rem;
}
.une {
  grid-column: 1 / -1;
  padding-bottom: 1rem;
  border-bottom: 5px solid var(--encre);
}
.colonne {
  padding: 0 1rem;
  border-left: 1px solid var(--encre);
}
article {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.bandeau {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: baseline;
  gap: 0.25rem 0.75rem;
  margin: 0;
  text-align: center;
  border-bottom: 1px solid var(--encre);
  padding-bottom: 0.25rem;
}
.bandeau-epoque {
  width: 100%;
  font-family: 'Alfa Slab One', serif;
  font-size: 1.1rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.une .bandeau-epoque {
  font-size: clamp(1.4rem, 5cqi, 2.4rem);
}
.nature {
  font-family: 'Playfair Display SC', serif;
  font-weight: 700;
}
.gravite {
  font-family: 'Playfair Display SC', serif;
  font-weight: 700;
  color: var(--rouge);
}
.article-titre {
  margin: 0;
  font-family: 'UnifrakturCook', serif;
  font-weight: 700;
  font-size: 1.6rem;
  line-height: 1.1;
  text-align: center;
  overflow-wrap: anywhere;
}
.une .article-titre {
  font-size: clamp(2rem, 7cqi, 3.75rem);
}
.texte {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
}
.resume {
  flex: 1 1 14rem;
  margin: 0;
}
.une .resume::first-letter {
  float: left;
  margin: 0.15rem 0.5rem 0 0;
  padding: 0 0.3rem;
  border: 4px double var(--encre);
  font-family: 'UnifrakturCook', serif;
  font-weight: 700;
  font-size: 3rem;
  line-height: 1;
  background: var(--papierClair);
}
.signalement {
  flex: 1 1 14rem;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.une .signalement {
  border: 6px double var(--encre);
  background: var(--papierClair);
  padding: 0.6rem 0.9rem;
}
.signalement-titre {
  margin: 0;
  font-family: 'Playfair Display SC', serif;
  letter-spacing: 0.2em;
  text-align: center;
  border-bottom: 1px solid var(--encre);
}
.colonne .signalement-titre {
  display: none;
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
  font-style: italic;
}
.action {
  margin: 0;
}
.une .action strong {
  color: var(--rouge);
}
.rubriques {
  margin: 0;
  font-size: 0.9rem;
  font-style: italic;
  text-align: center;
}
article :deep(.mentions) {
  font-size: 0.82rem;
  color: var(--discret);
  border-top: 1px solid var(--encre);
  padding-top: 0.35rem;
}
article :deep(.mentions a) {
  color: var(--rouge);
  font-weight: 700;
}
.gazette :deep(a:focus-visible) {
  outline: 3px solid var(--focus);
  outline-offset: 2px;
}
/* Colonnes empilées (téléphone) : filet horizontal au lieu du filet vertical. */
@container (max-width: 30rem) {
  .colonne {
    padding: 0.75rem 0 0;
    border-left: 0;
    border-top: 1px solid var(--encre);
  }
}
.pied {
  border-top: 5px solid var(--encre);
  padding-top: 0.4rem;
  font-family: 'Playfair Display SC', serif;
  font-size: 0.8rem;
}
</style>
