<script setup lang="ts">
// Champs de saisie d'un dépôt GitHub ou GitLab (INV-12), partagés par la création d'un projet et le rattachement
// d'un dépôt. Le serveur valide la saisie ; les champs vides sont omis à l'envoi (`depotDeSaisie`).
import type { SaisieDepot } from './alimentation'

const props = defineProps<{ prefixe: string; obligatoire?: boolean }>()
const saisie = defineModel<SaisieDepot>({ required: true })

function champ(nom: string): string {
  return `${props.prefixe}-${nom}`
}
</script>

<template>
  <div class="saisie-depot">
    <label :for="champ('forge')">Forge</label>
    <select :id="champ('forge')" v-model="saisie.forge">
      <option value="GITHUB">GitHub</option>
      <option value="GITLAB">GitLab</option>
    </select>
    <label :for="champ('depot')">Dépôt<template v-if="!obligatoire"> (facultatif)</template></label>
    <input
      :id="champ('depot')"
      v-model="saisie.depot"
      type="text"
      autocomplete="off"
      spellcheck="false"
      maxlength="200"
      placeholder="organisation/depot"
      :required="obligatoire"
    />
    <label :for="champ('branche')">Branche (facultative, main par défaut)</label>
    <input :id="champ('branche')" v-model="saisie.branche" type="text" autocomplete="off" spellcheck="false" />
    <label :for="champ('chemin-sbom')">Chemin de la SBOM dans le dépôt (facultatif)</label>
    <input :id="champ('chemin-sbom')" v-model="saisie.cheminSbom" type="text" autocomplete="off" spellcheck="false" />
    <label :for="champ('job-sbom')">Job qui produit la SBOM (facultatif)</label>
    <input :id="champ('job-sbom')" v-model="saisie.jobSbom" type="text" autocomplete="off" spellcheck="false" />
    <label :for="champ('manifestes')">Chemins des manifestes (facultatifs, un par ligne)</label>
    <textarea :id="champ('manifestes')" v-model="saisie.manifestes" rows="2" spellcheck="false"></textarea>
  </div>
</template>

<style scoped>
.saisie-depot { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 0.4rem 0.75rem; align-items: center; }
.saisie-depot input, .saisie-depot select, .saisie-depot textarea { max-width: 100%; font: inherit; }
/* Écran étroit : libellé au-dessus de son champ, sans défilement horizontal à 320 px (RGAA 10.11). */
@media (max-width: 40rem) {
  .saisie-depot { grid-template-columns: minmax(0, 1fr); }
}
</style>
