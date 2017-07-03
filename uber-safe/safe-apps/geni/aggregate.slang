defenv Selfie :-
  spec('Load the principal keypair'),
  principal($1).

defcon aggregatePolicySet() :- 
  {
     sliverInstantiate(Slice, Subject, Project) :-
       SA := rootId(Slice),
       SA: slice(Slice, Project, standard),
       sliceAuthority(SA), 
       SA: controlPrivilege(Subject, Slice, instantiate, _),
       geniUser(Subject).
 
     label('object/aggregate-policy-slice-operations').
  }.

defetch linkFetch(SetId1, SetId2, SetId3) :- [SetId1, SetId2, SetId3].

defguard authorize(Slice, Subject, Project, SetId1, SetId2, SetId3) :-
  spec('authorize the subject to access the slice on given project'),
  FetchAllSets := linkFetch(SetId1, SetId2, SetId3),
  {
     importAll($FetchAllSets).
     sliverInstantiate($Slice, $Subject, $Project)?
  }

defpost publishAggregatePolicy() :- [aggregatePolicySet()].

//definit publishAggregatePolicy().

definit authorize($2, $3, $4, $5, $6, $7).
