defetch fetchLink(?Link) :- [?Link].

defguard safesetsPost(?PostSetAsString) :-
  spec('verify the signature of the principal on the post set request'),
  ?PostSet              := parseSet(?PostSetAsString),
  ?Subject              := getSubject(?PostSet),
  ?Speaker              := getSpeaker(?PostSet),
  ?SpeakerKeyMayBe      := getSpeakerKey(?PostSet),
  /*
  ?SpeakerKey           := ifThenElse(=(?SpeakerKeyMayBe, nil), getPrincipal(fetchLink(?Speaker)), ?SpeakerKeyMayBe),
  ?Name                 := getName(?PostSet),
  ?SetId                := getId(?PostSet),
  ?SpeaksForRef         := fetchLink(getSpeakerRef(?PostSet)),
  ?IsSignatureValid     := verifySignature(?PostSet, ?SpeakerKey),
  {
    importAll($SpeaksForRef).

    query() :- $Subject: speaksFor($Speaker, $Subject), $IsSignatureValid.
    query() :- $Subject: speaksForOn($Speaker, $Subject, Name), $IsSignatureValid.
    query()?
  },
  //simplePost(?SetId, ?PostSetAsString)
  */
  true
end

defguard safesetsGet(?Id) :-
  simpleGet(?Id)
end
