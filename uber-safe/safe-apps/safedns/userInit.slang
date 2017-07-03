
defenv Selfie :-
  spec('Load the principal keypair'),
  principal?($1),
end

defcon makeMyIdSet?(?CN) :-
  spec('Construct identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
    commonName($CN).
  }
end

definit post?(makeMyIdSet?($2)).
