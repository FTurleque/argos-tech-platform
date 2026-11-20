<script setup lang="ts">
// Mise en page « fantaisie » : magazine décalé et coloré (ADR-0023, maquette « La une fantaisie »).
// L'humour est dans la forme, jamais dans les faits : surtitres et dessins sont décoratifs
// (aria-hidden), la nature, la gravité, les projets et les mentions restent ceux de l'élément.
// Couche mise en page : aucun appel réseau, aucun filtrage, aucun tri (AFF-02).
import '@fontsource/fraunces/latin-700.css'
import '@fontsource/fraunces/latin-900.css'
import '@fontsource/fraunces/latin-900-italic.css'
import '@fontsource/caveat/latin-700.css'
import '@fontsource/literata/latin-400.css'
import '@fontsource/literata/latin-400-italic.css'
import '@fontsource/literata/latin-700.css'
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

/** Surtitre décoratif de chaque nature, affiché avant la nature réelle de l'élément. */
const SURTITRES: Readonly<Record<CodeNature, string>> = {
  'avis-securite': 'Les intrus',
  'fiche-reglementaire': 'Le coin des décrets',
  'nouvelle-version': 'Les nouveautés',
  'fin-de-support': 'Avis de départ',
  echeance: 'Les prophéties',
  actualite: 'Le courrier',
}
const TEINTES = ['carte', 'menthe', 'jaune', 'rose', 'lavande'] as const
</script>

<template>
  <section class="fantaisie" :style="jetons" :aria-labelledby="`${id}-titre`" data-mise-en-page="fantaisie">
    <div class="magazine">
      <div class="bandeau">
        <span class="griffonnage" aria-hidden="true">à lire avant le café !</span>
        <span class="numero">
          Numéro du
          <time v-if="veille.composeeLe" :datetime="veille.composeeLe">{{ dateLongue(veille.composeeLe) }}</time>
          <span v-else>{{ dateLongue(null) }}</span>
        </span>
      </div>

      <header class="manchette">
        <Titre :id="`${id}-titre`" :niveau="niveau" class="titre">
          <span class="hors-champ">Argos</span>
          <span class="lettres" aria-hidden="true">
            <span class="l1">A</span><span class="l2">R</span><span class="l3">G</span>
            <svg class="oeil" viewBox="0 0 132 104" aria-hidden="true" focusable="false">
              <path d="M6 52 C30 10, 102 10, 126 52 C102 94, 30 94, 6 52 Z" class="oeil-blanc" />
              <circle cx="66" cy="52" r="26" class="oeil-iris" />
              <circle cx="66" cy="52" r="12" class="oeil-pupille" />
              <circle cx="73" cy="44" r="5" class="oeil-reflet" />
            </svg>
            <span class="l4">S</span>
          </span>
        </Titre>
        <p class="devise">Observer. Corréler. Comprendre. Anticiper.</p>
      </header>

      <p v-if="veille.exemple" class="exemple" data-exemple>Données d’exemple : ces éléments sont fictifs.</p>

      <section class="sommaire" :aria-labelledby="`${id}-rubriques`">
        <Titre :id="`${id}-rubriques`" :niveau="niveauElement" class="sommaire-titre">Au sommaire</Titre>
        <ul role="list" class="sommaire-liste">
          <li v-for="entree in rubriques" :key="cleRubrique(entree.rubrique)" data-rubrique>
            {{ entree.rubrique.libelle }} <span class="compte">{{ nombreElements(entree.nombre) }}</span>
          </li>
        </ul>
      </section>

      <ul role="list" class="cartes">
        <li
          v-for="(element, index) in veille.elements"
          :key="element.identifiant"
          :class="[index === 0 ? 'une' : 'breve', `teinte-${index === 0 ? 'carte' : TEINTES[index % TEINTES.length]}`]"
        >
          <article :aria-labelledby="`${id}-e${index}`" data-element>
            <p class="surtitre">
              <span v-if="element.gravite?.code === 'critique'" class="tampon" aria-hidden="true">Alerte<br />critique</span>
              <span class="manuscrit" aria-hidden="true">{{ SURTITRES[element.type.code] ?? 'En passant' }}</span>
              <span class="nature" data-champ="type">{{ element.type.libelle }}</span>
              <span v-if="element.gravite" class="gravite"
                >gravité <span data-champ="gravite">{{ element.gravite.libelle }}</span></span
              >
            </p>
            <Titre :id="`${id}-e${index}`" :niveau="niveauElement" class="article-titre" data-champ="titre">{{
              element.titre
            }}</Titre>
            <p v-if="element.resume" class="resume" data-champ="resume">{{ element.resume }}</p>
            <div class="projets">
              <span class="libelle">Qui est concerné ?</span>
              <ul v-if="element.projets.length" role="list" class="etiquettes">
                <li
                  v-for="(projet, rangProjet) in element.projets"
                  :key="rangProjet"
                  :class="['etiquette', `penche-${rangProjet % 3}`, { probable: projet.niveau?.code === 'PROBABLE' }]"
                >
                  <span data-champ="projet">{{ projet.nom }}</span
                  ><template v-if="projet.niveau"> · <span data-champ="niveau">{{ projet.niveau.libelle }}</span></template>
                </li>
              </ul>
              <span v-else data-champ="sans-projet">aucun projet visible</span>
            </div>
            <p v-if="element.action" class="action">
              <strong>Que faire ?</strong> <span data-champ="action">{{ element.action }}</span>
            </p>
            <p v-if="element.rubriques.length" class="rubriques">
              <span class="libelle">Rubriques :</span>
              <span v-for="rubrique in element.rubriques" :key="cleRubrique(rubrique)" class="rubrique" data-champ="rubrique">{{
                rubrique.libelle
              }}</span>
            </p>
            <MentionsObligatoires :mentions="element.mentions" />
          </article>
        </li>
      </ul>

      <footer class="pied">L’humour est dans la forme, jamais dans les faits : informations, sources et mentions restent exactes.</footer>
    </div>
  </section>
</template>

<style scoped>
.fantaisie {
  container-type: inline-size;
  background: var(--lavande);
  padding: 0.9rem;
  font-family: Literata, Georgia, serif;
  color: var(--encre);
  line-height: 1.5;
}
.magazine {
  background: var(--papier);
  border: 3px solid var(--encre);
  box-shadow: 6px 8px 0 var(--encre);
  padding: 1rem clamp(0.75rem, 4cqi, 2.75rem) 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.bandeau {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
}
.griffonnage {
  font-family: Caveat, cursive;
  font-weight: 700;
  font-size: 1.4rem;
  color: var(--sarcelle);
  transform: rotate(-2deg);
}
.numero {
  padding: 0.2rem 0.8rem;
  border: 2px dashed var(--encre);
  border-radius: 999px;
  font-weight: 700;
  font-size: 0.9rem;
}
.manchette {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  text-align: center;
}
.titre {
  margin: 0;
  font-family: Fraunces, Georgia, serif;
  font-weight: 900;
  font-size: clamp(3.25rem, 17cqi, 8rem);
  line-height: 0.95;
}
.lettres {
  display: inline-flex;
  align-items: center;
  gap: 0.05em;
}
.lettres > span {
  display: inline-block;
}
.l1 {
  transform: rotate(-6deg);
}
.l2 {
  transform: rotate(4deg) translateY(0.06em);
  color: var(--rouge);
}
.l3 {
  transform: rotate(-3deg);
  color: var(--sarcelle);
}
.l4 {
  transform: rotate(-5deg) translateY(0.05em);
}
.oeil {
  width: 0.9em;
  height: 0.72em;
  transform: rotate(5deg);
}
.oeil-blanc {
  fill: var(--carte);
  stroke: var(--encre);
  stroke-width: 8;
}
.oeil-iris {
  fill: var(--or);
  stroke: var(--encre);
  stroke-width: 6;
}
.oeil-pupille {
  fill: var(--encre);
}
.oeil-reflet {
  fill: var(--carte);
}
.hors-champ {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
.devise {
  margin: 0;
  font-family: Caveat, cursive;
  font-weight: 700;
  font-size: 1.7rem;
  color: var(--rouge);
}
.exemple {
  align-self: center;
  margin: 0;
  padding: 0.35rem 1rem;
  background: var(--encre);
  color: var(--surEncre);
  font-weight: 700;
  transform: rotate(-1deg);
}
.sommaire {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0.6rem;
}
.sommaire-titre {
  margin: 0;
  font-family: Fraunces, serif;
  font-weight: 900;
  font-size: 1.2rem;
}
.sommaire-liste {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.6rem;
}
.sommaire-liste li {
  padding: 0.35rem 0.9rem;
  background: var(--lavande);
  border: 2px solid var(--encre);
  font-weight: 700;
}
.sommaire-liste li:nth-child(odd) {
  transform: rotate(-1deg);
}
.sommaire-liste li:nth-child(even) {
  transform: rotate(1deg);
}
.compte {
  font-weight: 400;
  color: var(--violet);
}
.cartes {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 15rem), 1fr));
  gap: 1.5rem;
}
.cartes > li {
  border: 3px solid var(--encre);
  padding: 1rem 1.15rem;
}
.une {
  grid-column: 1 / -1;
}
.teinte-carte {
  background: var(--carte);
}
.teinte-menthe {
  background: var(--menthe);
}
.teinte-jaune {
  background: var(--jaune);
}
.teinte-rose {
  background: var(--rose);
}
.teinte-lavande {
  background: var(--lavande);
  border-style: dashed;
}
article {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}
.tampon {
  order: 9;
  margin-left: auto;
  flex: 0 0 auto;
  width: 6.5rem;
  height: 6.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--or);
  color: var(--encre);
  clip-path: polygon(50% 0%, 61% 18%, 82% 10%, 79% 32%, 100% 38%, 85% 54%, 98% 72%, 76% 74%, 72% 96%, 54% 84%, 38% 100%, 30% 79%, 8% 82%, 16% 62%, 0% 46%, 19% 36%, 12% 14%, 34% 18%);
  transform: rotate(12deg);
  font-family: Fraunces, serif;
  font-weight: 900;
  font-size: 0.72rem;
  line-height: 1;
  text-align: center;
  text-transform: uppercase;
}
.surtitre {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.2rem 0.6rem;
  margin: 0;
}
.manuscrit {
  font-family: Caveat, cursive;
  font-weight: 700;
  font-size: 1.5rem;
  color: var(--rouge);
}
.nature {
  font-weight: 700;
  color: var(--sarcelle);
}
.gravite {
  font-weight: 700;
  color: var(--rouge);
}
.article-titre {
  margin: 0;
  font-family: Fraunces, Georgia, serif;
  font-weight: 900;
  font-size: 1.45rem;
  line-height: 1.1;
  overflow-wrap: anywhere;
}
.une .article-titre {
  font-style: italic;
  font-size: clamp(1.8rem, 6cqi, 3.25rem);
  line-height: 1.02;
}
.resume {
  margin: 0;
}
.une .resume {
  font-size: 1.15rem;
}
.projets,
.rubriques {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
  font-size: 0.95rem;
}
.libelle {
  font-weight: 700;
}
.etiquettes {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin: 0;
  padding: 0;
  list-style: none;
}
.etiquette {
  padding: 0.3rem 0.75rem;
  background: var(--rose);
  border: 2px solid var(--encre);
  font-weight: 700;
}
.etiquette.probable {
  background: var(--jaune);
  border-style: dashed;
}
.penche-0 {
  transform: rotate(-2deg);
}
.penche-1 {
  transform: rotate(1.5deg);
}
.penche-2 {
  transform: rotate(-1deg);
}
.action {
  margin: 0;
}
.rubrique {
  padding: 0.05rem 0.6rem;
  border: 2px solid var(--encre);
  border-radius: 999px;
}
.teinte-carte :deep(.mentions) {
  color: var(--discret);
}
article :deep(.mentions) {
  font-size: 0.82rem;
}
article :deep(.mentions a) {
  color: var(--rouge);
  font-weight: 700;
}
.fantaisie :deep(a:focus-visible) {
  outline: 3px solid var(--focus);
  outline-offset: 3px;
}
.pied {
  font-size: 0.8rem;
  color: var(--discret);
}
@media (prefers-reduced-motion: no-preference) {
  .cartes > li {
    transition: transform 0.2s ease;
  }
  .cartes > li:hover {
    transform: rotate(-0.5deg);
  }
}
@container (max-width: 28rem) {
  .tampon {
    display: none;
  }
}
</style>
