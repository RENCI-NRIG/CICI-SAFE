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
  principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/speaksfor1.pem').

defenv Subject() :- u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps'.
defenv SpeaksForRef() :- u'Sq3Q1eBAzv-YddnfO0PShr5HYEwV_yOoFS_eVufroiM'.

defcon sampleRules() :-
  spec('a sample defcon for comparison'),
  sample{
    hello(?X, ?Y) :- run(?X, ?Y).
    output(?X, ?Y) :- run(?X, ?Y).
  }.

defcon speakerSigningForSubject() :-
  spec('test for speaker (issuer) signing the set for subject'),
  ca{
    subject($Subject, nil).   (* subject(?SubjectId, ?Subject) *)
    speaker(nil, $SpeaksForRef).  (* speaker(?SpeakerSubject, ?SpeaksForRef)  Internally, the first attribute of speaker() is $Self;  $SpeaksForRef refers to a SafeSet that issues a speaksFor statements *)
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

(* definit post(). *)
