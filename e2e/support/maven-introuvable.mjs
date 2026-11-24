// Lancé par Playwright à la place de Maven introuvable (support/maven.mjs, ligneServeurMaven) : échoue avec un
// message qui indique comment désigner Maven.
import { MESSAGE_MAVEN_INTROUVABLE } from './maven.mjs'

console.error(MESSAGE_MAVEN_INTROUVABLE)
process.exitCode = 1
