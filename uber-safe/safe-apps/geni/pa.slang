defenv Selfie :-
  spec('Load the principal keypair'),
  principal($1).

defcon memberSet() :- 
  {
    member(?User, ?Project) :-
      member(?User, ?Project, _).

    member(?User, ?Project, true) :-
      owner(?User, ?Project).
 
    member(?User, ?Project, ?Delegatable) :-
      ?Delegator: delegateMember(?User, ?Project, ?Delegatable),
      member(?Delegator, ?Project, true).
 
    memberPrivilege(?User, ?Project, instantiate, ?Delegatable) :- 
      member(?User, ?Project, ?Delegatable).

    memberPrivilege(?User, ?Project, ?Priv, ?Delegatable) :-
      ?Delegator: delegateMemberPrivilege(?User, ?Project, ?Priv, ?Delegatable),
      memberPrivilege(?Delegator, ?Project, ?Priv, true).
 
    name('object/standardProjectMembership') :- 
      spec('Object set for standard project membership policy issued by self').
  }.

defcon createProject(?SubjectId, ?Project, ?StandardProjectMembershipRef) :-
  {
    owner($SubjectId, $Project).
    project($Project).
    link($StandardProjectMembershipRef).
    name("capability/$Project/$SubjectId").
  }.

defpost publishMemberSet() :- [memberSet()].

defpost publishProject(?SubjectId) :- 
   ?StandardProjectMembershipRef := computeIdFromName($Self, 'object/standardProjectMembership'), 
   //?Project := scid(), // For testing, we hard code the project value
   ?ProjectRef := "?SubjectId:projectGuid",
   [createProject(?SubjectId, ?ProjectRef, ?StandardProjectMembershipRef)].

definit publishMemberSet(), publishProject($2).
