import { createGlobalStyle } from 'styled-components'
import base from './base.js'
import shell from './shell.js'
import Dashboard from './Dashboard.js'
import forms from './forms.js'
import quoteGame from './quoteGame.js'
import calendar from './calendar.js'
import personForms from './personForms.js'
import responsiveLayout from './responsiveLayout.js'
import map from './map.js'
import quotes from './quotes.js'
import cliques from './cliques.js'
import admin from './admin.js'
import recommendations from './recommendations.js'
import selfProfile from './selfProfile.js'
import myQuotes from './myQuotes.js'

// Keep the cascade across shared classes and responsive overrides.
export default createGlobalStyle`
  ${base}
  ${shell}
  ${Dashboard}
  ${forms}
  ${quoteGame}
  ${calendar}
  ${personForms}
  ${responsiveLayout}
  ${map}
  ${quotes}
  ${cliques}
  ${admin}
  ${recommendations}
  ${selfProfile}
  ${myQuotes}
`
