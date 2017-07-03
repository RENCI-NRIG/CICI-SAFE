defguard safesetsPost(?PostSetAsString) :-
  spec('verify the signature of the principal on the post set request'),
  ?PostSet              := parseSet(?PostSetAsString),
  ?Name                 := getName(?PostSet),
  ?Name                 != '',
  ?Subject              := getSubject(?PostSet),
  ?Speaker              := getSpeaker(?PostSet),
  ?SpeakerKeyMayBe      := getSpeakerKey(?PostSet),
  ?SpeakerKey           := ifThenElse(=(?SpeakerKeyMayBe, nil), getPrincipal(fetch(?Speaker)), ?SpeakerKeyMayBe),
  ?SetId                := getId(?PostSet),
  ?SpeaksForRef         := fetch(getSpeakerRef(?PostSet)),
  ?IsSignatureValid     := verifySignature(?PostSet, ?SpeakerKey),
  {
    import($SpeaksForRef).

    query() :- $Subject: speaksFor($Speaker, $Subject), $IsSignatureValid.
    query() :- $Subject: speaksForOn($Speaker, $Subject, $Name), $IsSignatureValid.
    query()?
  },
  simplePost(?SetId, ?PostSetAsString),
  true
end

defguard safesetsPost(?PostSetAsString) :-
  spec('case --- identity set'),
  ?PostSet              := parseSet(?PostSetAsString),
  ?Name                 := getName(?PostSet),
  ?Name                  = '',
  ?SetId                := getId(?PostSet),
  simplePost(?SetId, ?PostSetAsString),
  true
end
