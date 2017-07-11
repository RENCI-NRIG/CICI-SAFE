defguard safesetsPost(?PostSetAsString) :-
  spec('verify the signature of the principal on the post set request'),
  ?PostSet              := parseSet(?PostSetAsString),
  ?SetId                := getId(?PostSet),
  ?Speaker              := getSpeaker(?PostSet),
  ?SetId                != ?Speaker,
  ?Name                 := getName(?PostSet),
  ?Subject              := getSubject(?PostSet),
  ?SpeakerKeyMayBe      := getSpeakerKey(?PostSet),
  ?SpeakerKey           := ifThenElse(=(?SpeakerKeyMayBe, nil), getPrincipal(fetch(?Speaker)), ?SpeakerKeyMayBe),
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
  ?SetId                := getId(?PostSet),
  ?Speaker              := getSpeaker(?PostSet),
  ?SetId                =  ?Speaker,
  simplePost(?SetId, ?PostSetAsString),
  true
end
