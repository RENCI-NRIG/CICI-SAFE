defenv Selfie() :-
  principal('/home/vamsi/Code/safe/geni/user1_keyPair.pem').

defenv Issuer :- u'DCR9fcoV0bKPv6ZybvLBnKP7mtDdCtIE1xger5P416s'.

defcon subjectIssuingSpeaksFor() :-
  spec('subject issuing speaksFor statment for speaker'),
  simpleSpeaksFor{
    speaksFor($Issuer, $Self).
  }.

defcon subjectIssuingSpeaksForOn() :-
  spec('subject issuing speaksFor statment for speaker'),
  simpleSpeaksForOnNamespace{
    speaksForOn($Issuer, $Self, "$Self:$Issuer").
  }.

defpost post() :- [subjectIssuingSpeaksFor(), subjectIssuingSpeaksForOn()].

definit post().
