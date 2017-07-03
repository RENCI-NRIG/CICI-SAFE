defenv Selfie() :-
  spec('Mint a new key pair using RSA algorithm of size 4096 bits'),
  principal('/home/vamsi/Code/safe/geni/user2_keyPair.pem').

defenv Two :- hello, 2.

defcon firstSet() :-
  ?Two := 4, // Q: What is Self is redefined here? and published under Selfie's Self?
  firstSet{
    parent(a, b).
    parent(b, c).
  }.

defcon secondSet() :-
  ?FirstSet := computeIdFromName($Self, firstSet),
  secondSet{
    simple(World).
    ancestor(?X, ?Y) :- parent(?X, ?Y).
    ancestor(?X, ?Y) :- parent(?X, ?Z), ancestor(?Z, ?Y).
    link($FirstSet).
  }.

defpost postASet() :- [firstSet(), secondSet()].

defetch fetchASet() :- [computeIdFromName($Self, secondSet)].

defguard guardTest() :- 
  ?X := fetchASet(),
  {
    $Self: importAll($X).
    //simple(?X)?
    ancestor(?X, ?Y)?
  }.

//definit postASet(), guardTest().
//definit makeFunSet(), postASet().
definit guardTest().
