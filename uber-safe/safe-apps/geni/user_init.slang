
defenv Selfie :-
  spec('Load the principal keypair'),
  principal($1)
end

defcon makeMyIdSet(?CN) :-
  spec('Construct identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }
end

defcon makeASet(?CN) :-
  hello{
    hello(world).
    //hello($CN).
    hello(duke)~
  }
end

//definit post(makeMyIdSet(?CN)).
//definit fetch($2).
definit post(makeASet($2)).
