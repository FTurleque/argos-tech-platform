<script setup lang="ts">
// Mise en page « classique » : application de travail, fil de veille sobre (ADR-0023, maquette
// « Fil de veille de l'équipe »). Mise en page par défaut et de repli (AFF-04).
// Couche mise en page : aucun appel réseau, aucun filtrage, aucun tri (AFF-02).
import '@fontsource/inter/latin-400.css'
import '@fontsource/inter/latin-600.css'
import '@fontsource/inter/latin-700.css'
import '@fontsource/space-grotesk/latin-700.css'
import { computed, useId } from 'vue'
import MentionsObligatoires from '../../contenu/MentionsObligatoires.vue'
import { variablesCss } from '../contraste'
import type { ProprietesMiseEnPage } from '../modele'
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
</script>

<template>
  <section class="classique" :style="jetons" :aria-labelledby="`${id}-titre`" data-mise-en-page="classique">
    <header class="bandeau">
      <span class="marque" aria-hidden="true">ARGOS</span>
      <p class="bandeau-date">
        Veille composée le
        <time v-if="veille.composeeLe" :datetime="veille.composeeLe">{{ dateLongue(veille.composeeLe) }}</time>
        <span v-else>{{ dateLongue(null) }}</span>
      </p>
    </header>

    <div class="tete">
      <Titre :id="`${id}-titre`" :niveau="niveau" class="titre">Fil de veille</Titre>
      <p class="sous-titre">
        {{ nombreElements(veille.elements.length) }},
        {{ veille.exemple ? 'jeu d’exemple' : 'issus de vos profils actifs et de vos abonnements personnels' }}
      </p>
      <p v-if="veille.exemple" class="exemple" data-exemple>Données d’exemple : ces éléments sont fictifs.</p>
    </div>

    <div class="corps">
      <aside class="sommaire" :aria-labelledby="`${id}-rubriques`">
        <Titre :id="`${id}-rubriques`" :niveau="niveauElement" class="sommaire-titre">Rubriques</Titre>
        <ul role="list" class="sommaire-liste">
          <li v-for="r in rubriques" :key="cleRubrique(r.rubrique)" data-rubrique>
            <span>{{ r.rubrique.libelle }}</span>
            <span class="compte">{{ nombreElements(r.nombre) }}</span>
          </li>
        </ul>
      </aside>

      <ul role="list" class="fil">
        <li v-for="(element, index) in veille.elements" :key="element.identifiant">
          <article class="carte" :aria-labelledby="`${id}-e${index}`" data-element>
            <p class="meta">
              <span class="type" :class="`type-${element.type.code}`" data-champ="type">{{ element.type.libelle }}</span>
              <span v-if="element.gravite" class="gravite" :class="`gravite-${element.gravite.code}`"
                >Gravité <span data-champ="gravite">{{ element.gravite.libelle }}</span></span
              >
            </p>
            <Titre :id="`${id}-e${index}`" :niveau="niveauElement" class="carte-titre" data-champ="titre">{{
              element.titre
            }}</Titre>
            <p v-if="element.resume" class="resume" data-champ="resume">{{ element.resume }}</p>
            <div class="projets">
              <span class="libelle">Concerne :</span>
              <ul v-if="element.projets.length" role="list" class="puces">
                <li v-for="(p, ip) in element.projets" :key="ip" class="puce">
                  <span data-champ="projet">{{ p.nom }}</span
                  ><template v-if="p.niveau"> · <span data-champ="niveau">{{ p.niveau.libelle }}</span></template>
                </li>
              </ul>
              <span v-else data-champ="sans-projet">aucun projet visible</span>
            </div>
            <p v-if="element.action" class="action">
              <strong>À faire :</strong> <span data-champ="action">{{ element.action }}</span>
            </p>
            <p v-if="element.rubriques.length" class="rubriques">
              <span class="libelle">Rubriques :</span>
              <span v-for="r in element.rubriques" :key="cleRubrique(r)" class="rubrique" data-champ="rubrique">{{
                r.libelle
              }}</span>
            </p>
            <MentionsObligatoires :mentions="element.mentions" />
          </article>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.classique {
  container-type: inline-size;
  font-family: Inter, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  color: var(--encre);
  background: var(--fond);
  border-radius: 10px;
  overflow: hidden;
  line-height: 1.45;
}
.bandeau {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.25rem 1rem;
  padding: 0.75rem 1.25rem;
  background: var(--marine);
  color: var(--surMarine);
}
.marque {
  font-family: 'Space Grotesk', Inter, sans-serif;
  font-weight: 700;
  font-size: 1.2rem;
  letter-spacing: 0.08em;
}
.bandeau-date {
  margin: 0;
  font-size: 0.85rem;
  color: var(--surMarineDoux);
}
.tete {
  padding: 1.25rem 1.25rem 0.5rem;
}
.titre {
  margin: 0;
  font-family: 'Space Grotesk', Inter, sans-serif;
  font-weight: 700;
  font-size: 1.75rem;
  color: var(--marine);
}
.sous-titre {
  margin: 0.25rem 0 0;
  font-size: 0.9rem;
  color: var(--discret);
}
.exemple {
  display: inline-block;
  margin: 0.6rem 0 0;
  padding: 0.3rem 0.7rem;
  border-radius: 6px;
  background: var(--vigilanceFond);
  color: var(--vigilanceTexte);
  font-size: 0.85rem;
  font-weight: 600;
}
.corps {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  padding: 0.75rem 1.25rem 1.25rem;
}
.sommaire {
  flex: 1 1 12rem;
  max-width: 100%;
}
.sommaire-titre {
  margin: 0 0 0.4rem;
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--encre);
}
.sommaire-liste {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.25rem;
  font-size: 0.9rem;
}
.sommaire-liste li {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
}
.compte {
  white-space: nowrap;
}
.fil {
  flex: 999 1 20rem;
  min-width: 0;
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.75rem;
}
.carte {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem 1.15rem;
  background: var(--carte);
  border: 1px solid var(--bordure);
  border-radius: 10px;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin: 0;
}
.type,
.gravite {
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.15rem 0.5rem;
  border-radius: 6px;
}
.type-avis-securite,
.gravite-critique,
.gravite-elevee {
  background: var(--securiteFond);
  color: var(--securiteTexte);
}
.type-fin-de-support,
.type-echeance,
.gravite-moyenne {
  background: var(--vigilanceFond);
  color: var(--vigilanceTexte);
}
.type-nouvelle-version {
  background: var(--versionFond);
  color: var(--versionTexte);
}
.type-fiche-reglementaire {
  background: var(--reglementFond);
  color: var(--reglementTexte);
}
.type-actualite,
.gravite-faible {
  background: var(--puce);
  color: var(--encreDouce);
}
.carte-titre {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--marine);
  overflow-wrap: anywhere;
}
.resume,
.action {
  margin: 0;
  font-size: 0.95rem;
  color: var(--encreDouce);
}
.action strong {
  color: var(--encre);
}
.projets,
.rubriques {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  margin: 0;
  font-size: 0.8rem;
}
.libelle {
  color: var(--discret);
}
.puces {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin: 0;
  padding: 0;
  list-style: none;
}
.puce {
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  background: var(--puce);
  color: var(--encre);
}
.rubrique {
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  background: var(--rubriqueFond);
  color: var(--marine);
}
.carte :deep(.mentions) {
  color: var(--discret);
}
.carte :deep(.mentions a) {
  color: var(--lien);
  font-weight: 600;
}
.classique :deep(a:focus-visible) {
  outline: 3px solid var(--focus);
  outline-offset: 2px;
}
@container (max-width: 30rem) {
  .titre {
    font-size: 1.4rem;
  }
}
</style>
