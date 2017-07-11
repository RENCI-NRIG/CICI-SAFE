defenv Selfie() :-
  principal('/home/vamsi/Code/safe/geni/cp_keyPair.pem').

defenv Subject() :- u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps'.
defenv Speaker() :- u'DCR9fcoV0bKPv6ZybvLBnKP7mtDdCtIE1xger5P416s'.

defetch linkFetch() :- 
  ?Link = computeIdFromName($Subject, 'ca'),
  println(?Link),
  [?Link].

defetch negativeLinkFetch() :- 
  ?Link = computeIdFromName($Subject, 'negativeCa'),
  println(?Link),
  [?Link].

defguard speaksForTest() :-
  ?Link = linkFetch(),
  //?Link = negativeLinkFetch(),
  {
    importAll($Link).
    $Subject: said(speaksFor)? 
  }.

definit speaksForTest().
