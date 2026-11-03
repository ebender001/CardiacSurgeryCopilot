# On-device PHI screening assets

Used by `PHIFilterService` to find person names and place names in typed
text before it is sent anywhere. Everything here is read locally; nothing
is downloaded at runtime.

- `en-ner-person.bin`, `en-ner-location.bin` -- Apache OpenNLP 1.5 English
  named-entity models (Apache License 2.0), from the OpenNLP project's
  releases (https://sourceforge.net/projects/opennlp/files/models-1.5/).
  SHA-256:
  - person: 687a9263d96b37fced707c9f2ac0560f9edaf54658856395555901924f64dbe4
  - location: 8fe39e48633f4a86c4132d9c54b396a2d8e0460c1d71e3562dacf976984f447b
- `MedicalDictionary.txt`, `CVTMedicalDictionary.txt` -- the same word
  lists the iOS client bundles; a detected "name" or "place" that is a
  known medical term (e.g. "Whipple", "Lyme") is left alone.
