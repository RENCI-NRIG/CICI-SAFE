defenv Selfie :-
  spec('Load the principal keypair'),
  principal('/home/vamsi/Code/safe/geni/user2_keyPair.pem').

defenv NegativeInput() :- 
 '''
  cert #'application/slang' -> u'kM--9Mqbimxnsm0_iQy79LBL9QgqX2C5i9BSNBn8IAo'(
    signedData(
      version('1'),
      speaker(u'DCR9fcoV0bKPv6ZybvLBnKP7mtDdCtIE1xger5P416s', nil, u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps'),
      subject(u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps', nil),
      validity('2014-05-11T00:50:00.000-04:00', '2017-05-11T01:00:00.000-04:00'),
      cred #'application/slog' -> 'negativeCa'{
	said(?X) :- hello(?X).
	hello(speaksFor).
      },
      signatureAlgorithm('SHA256withRSA')
    ),
    signature(u'gvtrRSkOrVMTH8krqKF0scCwHsKQ50Q4toTE031TQ6Zpnn1qDxAZf9LhLw3i2RGSXIFq1F8FQXpRmRSDdRIIwN0I4T3d4RtkoLEGjB5wmuFtIiHrY3s2w9QqILQ9PKadwNqeUh6RShSp-GGo4-I4pnL-r1odTTHlWXoUKbuHBme6WdJJvGA0-ChjxXPkMNMq7Hor6TrpapPHnJV9CKt5a8xYlTCMW_0vfHvY3D6u6IauuongzuBY5zMJND0gJ9_7kLv5CDCc_mnqSN7P4loD6d2hx18Gcub6PirNgrvdxZQIiDAehVBgwVMHXvpNffLzv20GV2bfoahQY8y0Bfjbgg')
  ).
  '''
end

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
	u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps': said(?X) :- u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps': hello(?X).
	hello(speaksFor).
	u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps': hello(speaksFor).
      },
      signatureAlgorithm('SHA256withRSA')
    ),
    signature(u'gjwU2tmMkKL4ky2U5bnix1dzbR0T-DMLxiX6-SJD9nGTWen81YioG9JgOuYLE07POmzE8hKX-qhJ5WW7tvRexI9aC04APsWL1fvYYnZ_fi5VXWJRUvKKlYRuj_hGwMJnRbOR3K8sBm5-9tR3c3oiQyelNv3YUhe6slxPajzGiigCp8h8LubiPbEpHvuXLmJX9dZbPbmE5jfGm375ItxAg3gGcxV4EI-FQXvjWlTCxizHHlIvT4nbHTNeG4-3p28q84GFDFil7GNxp7D7r-zi3B-xDP-kO_QgcC-0thHFNaHE5rRQ97xP2IQEcG8PHiSSJKFptti1PhJ1j5Qd6oCmhA')
  ).
 '''
end

defetch fetchLink(?Link) :- [?Link].

//ifThenElse(?Cond, ?LeftVar, ?RightVar) :- ?Cond = true, ?LeftVal := ?LeftVar, ?LeftVal.
//ifThenElse(?Cond, ?LeftVar, ?RightVar) :- ?Cond = false, ?RightVal := ?RightVar, ?RightVal.

defguard authorizePostSimple(?PostSetAsString) :-
  spec('verify the signature of the principal on the post set request'),
  ?PostSet              := parseSet(?PostSetAsString),
  ?Subject              := getSubject(?PostSet),
  ?Speaker              := getSpeaker(?PostSet),
  ?SpeakerKeyMayBe      := getSpeakerKey(?PostSet),
  ?SpeakerKey           := ifThenElse(=(?SpeakerKeyMayBe, nil), getPrincipal(fetchLink(?Speaker)), ?SpeakerKeyMayBe),
  ?Name                 := getName(?PostSet),
  ?SpeaksForRef         := fetchLink(getSpeakerRef(?PostSet)),
  ?IsSignatureValid     := verifySignature(?PostSet, ?SpeakerKey),
  //println("IsSignatureValid: ?IsSignatureValid with speakerKey as ?SpeakerKey"),
  {
    importAll($SpeaksForRef).

    query() :- println("Subject: $Subject"), println("Speaker: $Speaker"), $Subject: speaksFor($Speaker, $Subject), println('TRUE'), $IsSignatureValid.
    query() :- $Subject: speaksForOn($Speaker, $Subject, Name), $IsSignatureValid.
    query()?
  }
end

defguard authorizePostSimple(?PostSetAsString, ?SpeakerKey) :-
  spec('verify the signature of the principal on the post set request'),
  ?PostSet              := parseSet(?PostSetAsString),
  ?Subject              := getSubject(?PostSet),
  ?Speaker              := getSpeaker(?PostSet),
  ?Name                 := getName(?PostSet),
  ?SpeaksForRef         := fetchLink(getSpeakerRef(?PostSet)),
  ?IsSignatureValid     := verifySignature(?PostSet, ?SpeakerKey),
  {
    importAll($SpeaksForRef).

    query() :- $Subject: speaksFor($Speaker, $Subject, _), $IsSignatureValid.
    query() :- $Subject: speaksForOn($Speaker, $Subject, Name, _), $IsSignatureValid.
    query()?
  }
end

//definit authorizePostSimple($Input, $SelfKey).
definit authorizePostSimple($Input).
