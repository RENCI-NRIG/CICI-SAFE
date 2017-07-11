defenv Selfie :-
  spec('Load the principal keypair'),
  principal($1).


definit endorse($2, $3).

defcon endorsePI(?PI) :-
  spec('Endorse a geni pi'),
  {
    geniUser($PI).
    geniPI($PI).
    name("endorse/pi/$PI").
  }.

defcon endorseUser(?User) :- 
  spec('Endorse a geni user'),
  {
    geniUser($User).
    name("endorse/user/$User").
  }.

defpost endorse(?PI, ?User) :- 
  [endorsePI(?PI), endorseUser(?User)].
