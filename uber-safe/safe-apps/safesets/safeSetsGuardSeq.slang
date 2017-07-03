defenv Selfie :-
  spec('Load the principal keypair'),
  principal('/home/vamsi/Code/safe/geni/user2_keyPair.pem').

defenv Input() :-
 '''
cert #'application/slang' -> u'yLXRbMlQsmdqxmcVyu5bJsJR7JzpcpHftpXd2HMqgEk'(
  signedData(
    version('1'),
    speaker(u'DCR9fcoV0bKPv6ZybvLBnKP7mtDdCtIE1xger5P416s', nil, u'Sq3Q1eBAzv-YddnfO0PShr5HYEwV_yOoFS_eVufroiM'),
    subject(u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps', nil),
    validity('2014-05-11T00:50:00.000-04:00', '2017-05-11T01:00:00.000-04:00'),
    cred #'application/slog' -> 'ca'{
      said(?X) :- hello(?X).
      hello(speaksFor).
    },
    signatureAlgorithm('SHA256withRSA')
  ),
  signature(u'qEPeCuNwBQmeqWwsXNl26KWw0W-lXryCBvQQgNsitTUGkZDtZjrF7ZSNYKJqJgZDZjj14Z3b9xf8QMdFkJegoWXUU_t3Y5rn6PSD-0VJ9ofSOVIkNJfbiPXun3edcmf1cxhwasI40cAB9OQKCO1nUmY0yAJBbg1oI1rKgC2Kn_3RPEBOL_Yw3SEcL8cbvWc1IHusadTDdjCa-5xmYPBNna2dsWk1AuhkI_Zq4XacbQa3gVb1DmrhvAo7oplUJwrPm0KCSe-9Mxke7BF8fcu78bdpNjv0maUm9qd9idiASBZCxEIWc3RJzjWuh253x8uyYRAli-nDhM_yFyCG7YTO4A')
).
 '''
end

defetch fetchLink(?Link) :- [?Link].

defguard authorizePost(?PostSetAsString) :-
  spec('verify the signature of the principal on the post set request'),
  ?PostSet              := parseSet(?PostSetAsString),
  ?Subject              := getSubject(?PostSet),
  ?Speaker              := getSpeaker(?PostSet),
  ?SpeakerKeyMayBe      := getSpeakerKey(?PostSet),
  ?SpeakerKey           := ifThenElse(=(?SpeakerKeyMayBe, nil), getPrincipal(fetchLink(?Speaker)), ?SpeakerKeyMayBe),
  ?Name                 := getName(?PostSet),
  ?SpeaksForRef         := fetchLink(getSpeakerRef(?PostSet)),
  ?IsSignatureValid     := verifySignature(?PostSet, ?SpeakerKey),
  println("IsSignatureValid: ?IsSignatureValid with speakerKey as ?SpeakerKey"),
  {
    importAll($SpeaksForRef).

    query() :- $Subject: speaksFor($Speaker, $Subject), $IsSignatureValid.
    query() :- $Subject: speaksForOn($Speaker, $Subject, Name), $IsSignatureValid.
    query()?
  }
end

definit authorizePost($Input).

defguard check(?Link) :- 
  ?Ref = fetchLink(?Link),
  {
    importAll($Ref).
    ?Sub: speaksFor(?Spkr, ?Sub)?
  }.


//definit check('Sq3Q1eBAzv-YddnfO0PShr5HYEwV_yOoFS_eVufroiM').
