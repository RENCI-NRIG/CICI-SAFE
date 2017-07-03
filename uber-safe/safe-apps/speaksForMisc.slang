(*
defenv Selfie :- principal('/home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/user1_keyPair.pem').

defenv Issuer :- u'DCR9fcoV0bKPv6ZybvLBnKP7mtDdCtIE1xger5P416s'.

defcon subjectIssuingSpeaksFor() :-
  spec('subject issuing speaksFor statment for speaker'),
  simpleSpeaksFor{
    speaksFor($Issuer, $Self).
  }
end

defpost postSet(?SetId) :- [?SetId].

defenv Issuer :- u'DCR9fcoV0bKPv6ZybvLBnKP7mtDdCtIE1xger5P416s'.

defcon subjectIssuingSpeaksForOn() :-
  spec('subject issuing speaksFor statment for speaker'),
  simpleSpeaksForOnNamespace{
    speaksForOn($Issuer, $Self, "$Self:$Issuer").
  }
end



//definit _ := postSet(subjectIssuingSpeaksFor()).
definit _ := postSet(subjectIssuingSpeaksForOn()).

*)

defenv Selfie() :-
  principal('/home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/user2_keyPair.pem').

defenv Subject() :- u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps'.
defenv SpeaksForRef() :- u'Sq3Q1eBAzv-YddnfO0PShr5HYEwV_yOoFS_eVufroiM'.

defcon speakerSigningForSubject() :-
  spec('test for speaker (issuer) signing the set for subject'),
  ca{
    subject($Subject, nil).
    speaker(nil, $SpeaksForRef).
    hello(speaksFor).
    said(?X) :- hello(?X).
  }.

defcon negativeSpeakerSigningForSubject() :-
  spec('test for speaker (issuer) signing the set for subject'),
  negativeCa{
    subject($Subject, nil).
    speaker(nil, $Subject).
    hello(speaksFor).
    said(?X) :- hello(?X).
  }.


defpost post() :- [speakerSigningForSubject(), negativeSpeakerSigningForSubject()].

definit post().
