defguard authorizePost(?PostSetAsString) :-
  spec('verify the signature of the principal on the post set request'),
  ?PostSet      := parseSet(?PostSetAsString),
  ?Speaker      := getSpeakerId(?PostSet),
  ?Subject      := getSubjectId(?PostSet),
  ?ValidityFrom := getValidityFrom(?PostSet),
  ?ValidityUntil:= getValidityUntil(?PostSet),
  ?SigAlg       := getSignatureAlgorithm(?PostSet),
  ?Sig          := getSignature(?PostSet),
  ?Name         := getName(?PostSet),
  ?SignedData   := getSignedData(?PostSet),
  ?ComputedSig  := computeSignature(?SignedData, ?Speaker, ?SigAlg),
  ?SpeaksForRef := getSpeakerRef(),
  {
    importAll($SpeaksForRef).

    query() :- $Subject: speaksFor($Speaker, $Subject, true), $ComputedSig = $Sig.
    query() :- $Subject: speaksForOn($Speaker, $Subject, Name, true), $ComputedSig = $Sig. // last attr is delegation
    query()?
  }
end

defetch linkFetch(?Link) :- [?Link].


defenv Input() :- 
 '''
 cert #'application/slang' -> u'Sq3Q1eBAzv-YddnfO0PShr5HYEwV_yOoFS_eVufroiM'(
  signedData(
    version('1'),
    speaker(u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps', nil, nil),
    subject(u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps', nil),
    validity('2014-05-11T00:50:00.000-04:00', '2017-05-11T01:00:00.000-04:00'),
    cred #'application/slog' -> 'simpleSpeaksFor'{
      speaksFor(u'DCR9fcoV0bKPv6ZybvLBnKP7mtDdCtIE1xger5P416s', u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps').
    },
    signatureAlgorithm('SHA256withRSA')
  ),
  signature(u'BY4OhqjscYGlHDgbPrVjdj7QGLiW_qgPiLv7YFJxNT9ZTIvs3Mwj6snfI-StvxrgA8W7RkM3YA4IKiTEhbIXjW-jGwToMPNz0yE5we8UcDXdQkTrNHcn188YVEUaHPMo3zoNC9TA19Kn8ZyxLAtZnZkuW_aqb1WtH4-0yG6zN1O-8yN_CkAbzWFw-C577h5Mpg__HmBAUli8A3_049d-il73pHxBelEr51Tn2TiV7gbjORXxeqD5XmKnKlvkN5j-RQtF1ahdCPBc2iJM6-FxocQZdrXgYmrB_1zy7ExspeMLnswGoXpjZ1G9YmB6AuaWsj_j49jRi_aWYY-IrdZZAw')
). 

  '''
end

defenv Selfie :-
  spec('Load the principal keypair'),
  principal('/home/vamsi/Code/safe/geni/user1_keyPair.pem').

// why should we care about whether the set is valid or not? i.e., verifying the dates
defguard authorizePostSimple(?PostSetAsString, ?SpeakerKey) :-
  spec('verify the signature of the principal on the post set request'),
  ?PostSet              := parseSet(?PostSetAsString),
  ?Subject              := getSubject(?PostSet),
  ?Speaker              := getSpeaker(?PostSet),
  ?Name                 := getName(?PostSet),
  ?SpeaksForRef         := linkFetch(getSpeakerRef(?PostSet)),
  ?IsSignatureValid     := verifySignature(?PostSet, ?SpeakerKey),
  {
    importAll($SpeaksForRef).

    query() :- $Subject: speaksFor($Speaker, $Subject, _), $IsSignatureValid.
    query() :- $Subject: speaksForOn($Speaker, $Subject, Name, _), $IsSignatureValid.
    query()?
  }
end

definit authorizePostSimple($Input, $SelfKey).
