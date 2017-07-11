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

parse(?PostSet) :- ?PostSet := parseSet($Input).

defguard authorizePostSimple(?PostSetAsString) :-
  spec('verify the signature of the principal on the post set request'),
  ?PostSet              := parseSet(?PostSetAsString),
  ?Signature            := getSignature(?PostSet),
  ?Speaker              := getSpeaker(?PostSet),
  ?Subject              := getSubject(?PostSet),
  ?Name                 := getName(?PostSet),
  ?SpeaksForRef         := getSpeakerRef(?PostSet),
  {
    importAll($SpeaksForRef).

    query() :- $Subject: speaksFor($Speaker, $Subject, true), $ComputedSignature = $Signature.
    query() :- $Subject: speaksForOn($Speaker, $Subject, Name, true), $ComputedSignature = $Signature. // last attr is delegation
    query()?
  }
end

//parse(?PostSet)?

definit authorizePostSimple($Input).
