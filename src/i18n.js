// Minimal UI translations. Polish is the default; English is opt-in via localStorage 'meeter.locale' = 'en'.
const messages = {
  pl: {
    'recommendations.section': 'Eksperymentalne podpowiedzi uczestników',
    'recommendations.label': 'Model podpowiedzi',
    'recommendations.experimental': 'eksperyment',
    'recommendations.loading': 'Sprawdzanie modeli…',
    'recommendations.disabled': 'Wyłączone — wybieraj osoby ręcznie',
    'recommendations.suggestBusy': 'Szukanie propozycji…',
    'recommendations.suggest': 'Zaproponuj uczestników',
    'recommendations.hintGuests': 'Uwzględnia nazwę, miejsca i już wybrane osoby.',
    'recommendations.hintContext': 'Uwzględnia nazwę i miejsca.',
    'recommendations.hintAuto': 'Propozycje odświeżają się automatycznie po zmianie formularza. Kliknij osobę, aby ją dodać.',
    'recommendations.retrying': 'Usługa modeli się uruchamia — ponawiam sprawdzenie…',
    'recommendations.noModels': 'Brak modeli dla Twojego konta. Sprawdź wytrenowane pliki i środowisko Python, a następnie uruchom ponownie backend.',
    'recommendations.recheck': 'Sprawdź ponownie',
    'recommendations.noMore': 'Brak kolejnych propozycji dla tego zestawu.',
    'recommendations.add': 'Dodaj',
    'recommendations.recommended': 'zalecany',
    'recommendations.modeGuests': 'nazwa, miejsce i goście',
    'recommendations.modeContext': 'nazwa i miejsce',
  },
  en: {
    'recommendations.section': 'Experimental participant suggestions',
    'recommendations.label': 'Suggestion model',
    'recommendations.experimental': 'experimental',
    'recommendations.loading': 'Checking models…',
    'recommendations.disabled': 'Off — choose people manually',
    'recommendations.suggestBusy': 'Finding suggestions…',
    'recommendations.suggest': 'Suggest participants',
    'recommendations.hintGuests': 'Uses the name, places and people already selected.',
    'recommendations.hintContext': 'Uses the name and places.',
    'recommendations.hintAuto': 'Suggestions refresh automatically when the form changes. Click a person to add them.',
    'recommendations.retrying': 'The suggestion service is starting — checking again…',
    'recommendations.noModels': 'No models are available for your account. Check the trained files and the Python environment, then restart the backend.',
    'recommendations.recheck': 'Check again',
    'recommendations.noMore': 'No more suggestions for this set.',
    'recommendations.add': 'Add',
    'recommendations.recommended': 'recommended',
    'recommendations.modeGuests': 'name, place and guests',
    'recommendations.modeContext': 'name and place',
  },
}

export function getLocale() {
  try { return localStorage.getItem('meeter.locale') === 'en' ? 'en' : 'pl' } catch { return 'pl' }
}

export function t(key) {
  return messages[getLocale()][key] ?? messages.pl[key] ?? key
}
