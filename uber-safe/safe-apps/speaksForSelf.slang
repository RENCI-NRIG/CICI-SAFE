defenv Selfie() :-
  principal('/home/vamsi/Code/safe/geni/cp_keyPair.pem').

defguard speaksForTest() :-
  {
    $Self: speaksFor($Self, $Self, false)? 
    $Self: speaksForOn($Self, $Self, objectName, false)? 
  }.

definit speaksForTest().
