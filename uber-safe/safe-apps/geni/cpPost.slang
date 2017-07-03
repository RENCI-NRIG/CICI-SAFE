defenv Selfie :-
  spec('Load the principal keypair'),
  principal($1).

defcon aggregatePolicySet() :- 
  {
     sliverInstantiate(?Slice, ?Subject, ?Project) :-
       ?SA := rootId(?Slice),
       ?SA: slice(?Slice, ?Project, standard),
       sliceAuthority(?SA), 
       ?SA: controlPrivilege(?Subject, ?Slice, instantiate, _),
       geniUser(?Subject).
 
     name('object/aggregate-policy-slice-operations').
  }.

defpost publishAggregatePolicy() :- [aggregatePolicySet()].

definit publishAggregatePolicy().
