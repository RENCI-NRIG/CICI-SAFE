//defenv Selfie() :-
//  spec('Load the principal keypair'),
//  principal($1).

defenv Version() :- "set6".
defenv PolicyVersion() :- "policy6".
defenv GeniRoot() :- u'w5o9SVFI94-7hYMIUFeAbwunD1pMX_agEnohn8-yBZo'.

defenv MyAnchorSet() :- anchorSet().

//
// Identity set (apply to all participants)
//
defcon makeMyIdentitySet(?CN) :-
  spec("Construct the identity set for self"),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defcon makeMySubjectSet() :-
  spec("Construct the subject set for self"),
  {
    link($Self).
    label("subject($Self)($Version)").
  }.

defcon addTokenToSubjectSet(?Token) :-
  spec("Add a token to the user's subject set. Invoke after the user gets a delegation"),
  {
    link($Token).
    label("subject($Self)($Version)").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postSubjectSet() :- [makeMySubjectSet()].

defpost updateSubjectSet(?Token) :- [addTokenToSubjectSet(?Token)].


//
// Federation policy: trust structure and user/PI registration
//

defcon trustStructure() :-
  spec('Federation trust structure: adopted by all participants'),
  {
    memberAuthority(?MA) :- geniRoot(?Geni), ?Geni: memberAuthority(?MA).
    sliceAuthority(?SA) :- geniRoot(?Geni), ?Geni: sliceAuthority(?SA).
    projectAuthority(?PA) :- geniRoot(?Geni), ?Geni: projectAuthority(?PA).
    geniProxy(?GeniProxy) :- geniRoot(?Geni), ?Geni: geniProxy(?GeniProxy).
    aggregate(?Agg) :- geniRoot(?Geni), ?Geni: aggregate(?Agg).
    gmoc(?GeniMOC) :- geniRoot(?Geni), ?Geni: gmoc(?GeniMOC).
    label('geni/trust-structure($PolicyVersion)').
  }.

defcon registeredUserPolicy() :-
  spec('Federation policy for registered users and PIs: adopted by all participants'),
  {
    geniUser(?User) :- memberAuthority(?MA), ?MA: geniUser(?User).
    geniPI(?User) :- memberAuthority(?MA), ?MA: geniPI(?User).
    label('geni/registered-user($PolicyVersion)').
  }.

definit trustStructure(), registeredUserPolicy().

//
// Federation root: endorsements
//

defcon endorseMA(?MA) :-
  spec('endorse a member authority'),
  {
    memberAuthority($MA) :- spec("geni endorses a member authority $MA").
    label("ma/$MA($Version)").
  }.

defcon endorsePA(?PA) :-
  spec('endorse a project authority'),
  {
    projectAuthority($PA) :- spec("geni endorses a project authority $PA").
    label("pa/$PA($Version)").
  }.

defcon endorseSA(?SA) :-
  spec('endorse a slice authority'),
  {
    sliceAuthority($SA) :- spec("geni endorses a slice authority $SA").
    label("sa/$SA($Version)").
  }.

defcon endorseCP(?CP) :-
  spec('endorse an aggregate'),
  {
    aggregate($CP) :- spec("geni endorses an aggregate $CP").
    label("aggregate/$CP($Version)").
  }.

defpost postMAEndorsement(?MA) :- [endorseMA(?MA)].
defpost postPAEndorsement(?PA) :- [endorsePA(?PA)].
defpost postSAEndorsement(?SA) :- [endorseSA(?SA)].
defpost postCPEndorsement(?CP) :- [endorseCP(?CP)].


//
// MA: endorsements for registered users and PIs.
//

defcon endorsePI(?PI) :-
  spec('Endorse a geni PI'),
  ?SubjectSetRef := label($Self, "subject($Self)($Version)"),
  {
    geniUser($PI).
    geniPI($PI).
    link($SubjectSetRef).
    label("pi/$PI($Version)").
  }.

defcon endorseUser(?User) :- 
  spec('Endorse a geni user'),
  ?SubjectSetRef := label($Self, "subject($Self)($Version)"),
  {
    geniUser($User).
    link($SubjectSetRef).
    label("user/$User($Version)").
  }.

defpost postPIEndorsement(?PI) :- [endorsePI(?PI)].
defpost postUserEndorsement(?User) :- [endorseUser(?User)].


//
// PA: projects and their policy sets
//

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
      delegatesMemberPrivilege(?Delegator, ?User, ?Project, ?Priv, ?Delegatable),
      memberPrivilege(?Delegator, ?Project, ?Priv, true).

    delegatesMember(?Delegator, User, ?Project, ?Delegatable) :-
      ?Delegator: delegateMember(?User, ?Project, ?Delegatable).

    delegatesMemberPrivilege(?Delegator, ?User, ?Project, ?Priv, ?Delegatable) :-
      ?Delegator: delegateMemberPrivilege(?User, ?Project, ?Priv, ?Delegatable).
 
    label('pa/standard-project-membership($PolicyVersion)') :- 
      spec('Standard project membership policy').
  }.

defpost postMemberSet() :- 
  [memberSet()].

defcon delegateProjectMembership(?SubjectId, ?ProjectId, ?Delegatable) :-
  ?SubjectSetRef := label($Self, "subject($Self)($Version)"),
  {
    delegateMember($SubjectId, $ProjectId, $Delegatable).
    link($SubjectSetRef).
    label("project membership delegation $SubjectId $ProjectId($Version)").
  }.

defpost postProjectMembership(?SubjectId, ?ProjectId, ?Delegatable) :- 
  [delegateProjectMembership(?SubjectId, ?ProjectId, ?Delegatable)].


//
// PA authZ policy for project creation.
//
defcon projectPolicySet() :-
  {
    approveProject(?Owner) :-
      true.
      //geniPI(?Owner).
    label('pa/standard-project-operations($PolicyVersion)').
  }.

definit projectPolicySet().

defcon createProjectSet(?SubjectId, ?ProjectId, ?ProjectMembershipRef) :-
  ?SubjectSetRef := label($Self, "subject($Self)($Version)"),
  {
    owner($SubjectId, $ProjectId).
    project($ProjectId, standard).
    link($ProjectMembershipRef).
    link($SubjectSetRef).
    label("project/$ProjectId($Version)").
  }.

defpost postProjectSet(?SubjectId, ?ProjectId, ?ProjectMembershipRef) :- 
  [createProjectSet(?SubjectId, ?ProjectId, ?ProjectMembershipRef)].

//
// SA: slices and their policy sets
//

defcon standardSliceControlSet() :- 
  {
    controls(?Subject, ?Slice, true) :-
      owner(?Subject, ?Slice).
 
    controls(?Subject, ?Slice, ?Delegatable) :- 
      delegatesControl(?Delegator, ?Subject, ?Slice, ?Delegatable),
      controls(?Delegator, ?Slice, true).
 
    controls(?Subject, ?Slice) :-
      controls(?Subject, ?Slice, _).

    controlPrivilege(?Subject, ?Slice, stitch, ?Delegator) :- 
      controls(?Subject, ?Slice, ?Delegator).
 
    controlPrivilege(?Subject, ?Slice, instantiate, ?Delegator) :- 
      controls(?Subject, ?Slice, ?Delegator).
 
    controlPrivilege(?Subject, ?Slice, ?Priv, ?Delegatable) :- 
      delegatesControlPrivilege(?Delegator, ?Subject, ?Slice, ?Priv, ?Delegatable),
      controlPrivilege(?Delegator, ?Slice, ?Priv, true).

    delegatesControl(?Delegator, ?Subject, ?Slice, ?Delegatable) :-
      ?Delegator: delegateControl(?Subject, ?Slice, ?Delegatable).

    delegatesControlPrivilege(?Delegator, ?Subject, ?Slice, ?Priv, ?Delegatable) :-
      ?Delegator: delegatesControlPrivilege(?Subject, ?Slice, ?Priv, ?Delegatable).

    label('sa/standard-slice-control($PolicyVersion)') :- 
      spec('Standard slice control policy').
  }.

defpost postStandardSliceControlSet() :- 
  [standardSliceControlSet()].


defcon standardSliceDefaultPrivilegeSet() :-
  {
    controlPrivilege(?Subject, ?Slice, info, ?Delegatable) :-
      slice(?Slice, ?Project, standard),
      ?PA := rootPrincipal(?Project),
      projectAuthority(?PA),
      ?PA: project(?Project, standard),
      ?PA: memberPrivilege(?Subject, ?Project, info, ?Delegatable).
 
    controlPrivilege(?Subject, ?Slice, stop, true) :- 
      slice(?Slice, ?Project, standard),
      ?PA := rootPrincipal(?Project),
      projectAuthority(?PA),
      ?PA: project(?Project, standard),
      ?PA: owner(?Subject, ?Project).

    controlPrivilege(?Subject, ?Slice, stitch, true) :- 
      slice(?Slice, ?Project, standard),
      ?PA := rootPrincipal(?Project),
      projectAuthority(?PA),
      ?PA: project(?Project, standard),
      ?PA: owner(?Subject, ?Project).

    controlPrivilege(?Subject, ?Slice, stitch) :- 
      slice(?Slice, ?Project, standard),
      gmoc(?Subject).
 
    controlPrivilege(?Subject, ?Slice, info) :- 
      slice(?Slice, ?Project, standard),
      gmoc(?Subject).
 
    controlPrivilege(?Subject, ?Slice, stop) :- 
      slice(?Slice, ?Project, standard),
      gmoc(?Subject).
 
    label('sa/standard-slice-privilege($PolicyVersion)') :- 
      spec('Standard slice default privilege policy').
  }.

defpost postStandardSliceDefaultPrivilegeSet() :- 
  [standardSliceDefaultPrivilegeSet()].

defcon delegateSliceControl(?SubjectId, ?SliceId, ?Delegatable) :-
  ?SubjectSetRef := label($Self, "subject($Self)($Version)"),
  {
    delegateControl($SubjectId, $SliceId, $Delegatable).
    link($SubjectSetRef).
    label("slice control delegation $SubjectId $SliceId($Version)").
  }.

defpost postSliceControl(?SubjectId, ?SliceId, ?Delegatable) :- 
  [delegateSliceControl(?SubjectId, ?SliceId, ?Delegatable)].


//
// SA authZ policy for slice creation.
//
defcon slicePolicySet() :-
  {
    approveSlice(?Subject, ?Project) :-
      ?PA := rootPrincipal(?Project),
      projectAuthority(?PA),
      ?PA: project(?Project, standard),
      ?PA: memberPrivilege(?Subject, ?Project, instantiate, _),
      geniUser(?Subject).

    label('sa/standard-slice-operations($PolicyVersion)').
  }.

definit slicePolicySet().

//
// A newly approved slice is created as a set with links to its policy sets.
//

defcon createSliceSet(?SubjectId, ?SliceId, ?ProjectId, ?SliceControlRef, ?SlicePrivRef) :-
  ?SubjectSetRef := label($Self, "subject($Self)($Version)"),
  {
    owner($SubjectId, $SliceId).
    slice($SliceId, $ProjectId, standard).
    link($SliceControlRef).
    link($SlicePrivRef).
    link($SubjectSetRef).
    label("slice/$SliceId($Version)").
  }.

defpost postSliceSet(?SubjectId, ?SliceId, ?ProjectId, ?SliceControlRef, ?SlicePrivRef) :- 
  [createSliceSet(?SubjectId, ?SliceId, ?ProjectId, ?SliceControlRef, ?SlicePrivRef)].

//
// Aggregate
//

defcon aggregatePolicySet() :- 
  {
     approveSliver(?Subject, ?Slice) :-
       ?SA := rootPrincipal(?Slice),
       ?SA: slice(?Slice, ?Project, standard),
       sliceAuthority(?SA), 
       ?SA: controlPrivilege(?Subject, ?Slice, instantiate, _),
       geniUser(?Subject).
 
     label('aggregate/standard-slice-operations($PolicyVersion)').
  }.

definit aggregatePolicySet().

//
// Omitted: createSliver
// For this demo/benchmark, we're just checking approveSliver authorizations.
// createSliver would show how to represent a sliver and its resources in trust logic.  TBD.
//

//
// Guards
// There are three for primary GENI interfaces:
// pa.createProject, sa.createSlice, agg.createSliver.
// AuthZ for other agg slice operations is similar to approveSliver.
//

defguard createProject() :-
  spec("Guard for ProjectAuthority.createProject"),
  ?PolicyRef := label("pa/standard-project-operations($PolicyVersion)"),
  {
    link($MyAnchorSet).
    link($PolicyRef).
    link($BearerRef).
    approveProject($Subject)?
  }.

defguard createSlice(?ProjectId) :-
  spec("Guard for SliceAuthority.createSlice"),
  ?PolicyRef := label("sa/standard-slice-operations($PolicyVersion)"),
  {
    link($MyAnchorSet).
    link($PolicyRef).
    link($BearerRef). 
    approveSlice($Subject, $ProjectId)?
  }.

defguard createSliver(?SliceId) :-
  spec("Guard for Aggregate.createSliver"),
  ?PolicyRef := label("aggregate/standard-slice-operations($PolicyVersion)"),
  {
    link($MyAnchorSet).
    link($PolicyRef).
    link($BearerRef). 
    approveSliver($Subject, $SliceId)?
  }.

//
// Local example
//

defcon fedEndorsementStub() :-
  ?E1 := endorseMA($Self),
  ?E2 := endorsePA($Self),
  ?E3 := endorseSA($Self),
  ?E4 := endorseCP($Self),
  {
    link($E1).
    link($E2).
    link($E3).
    link($E4).
    label("geni/endorse-self-stub($Version)").
  }.


//
// Disabled the following from anchorSet()
// Caused infinite loops
// ?FedEndorseRef := fedEndorsementStub(),
//  link($FedEndorseRef).
//

defcon anchorSet() :-
  ?FedStructureRef := label("geni/trust-structure($PolicyVersion)"),
  ?FedUserRef := label("geni/registered-user($PolicyVersion)"),   
  {
    geniRoot($GeniRoot).
    link($FedStructureRef).
    link($FedUserRef).   
    label("geni/anchor-set($Version)").
  }.

