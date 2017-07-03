

//
// Federation policy: trust structure and user/PI registration
//

defcon trustStructure() :-
  spec('Federation trust structure: adopted by all participants'),
  {
    identityProvider(?IdP) :- geniRoot(Geni), Geni: identityProvider(?IdP).
    sliceAuthority(?SA) :- geniRoot(Geni), Geni: sliceAuthority(?SA).
    projectAuthority(?PA) :- geniRoot(?Geni), Geni: projectAuthority(?PA).
    geniProxy(?GeniProxy) :- geniRoot(?Geni), Geni: geniProxy(?GeniProxy).
    aggregate(?Agg) :- geniRoot(?Geni), Geni: aggregate(?Agg).
    gmoc(?GeniMOC) :- geniRoot(?Geni), Geni: gmoc(?GeniMOC).
    label('geni/trust-structure').
  }.

defcon registeredUserPolicy() :-
  spec('Federation policy for registered users and PIs: adopted by all participants'),
  {
    geniUser(?User) :- identityProvider(?IdP), ?IdP: geniUser(?User).
    geniPI(?User) :- identityProvider(?IdP), ?IdP: geniPI(?User).
    label('geni/registered-user').
  }.

//
// Federation root: endorsements
//

defcon endorseIdP(?IdP) :-
  spec('endorse an identity provider'),
  {
    identityProvider(?IdP) :- spec("geni endorses an identity provider $IdP").
    label("idp/$IdP").
  }.

defcon endorsePA(?PA) :-
  spec('endorse a project authority'),
  {
    projectAuthority(?PA) :- spec("geni endorses a project authority $PA").
    label("pa/$PA").
  }.

defcon endorseSA(?SA) :-
  spec('endorse a slice authority'),
  {
    sliceAuthority(?SA) :- spec("geni endorses a slice authority $SA").
    label("sa/$SA").
  }.

defcon endorseCP(?CP) :-
  spec('endorse an aggregate'),
  {
    aggregate(?CP) :- spec("geni endorses an aggregate $CP").
    label("aggregate/$CP").
  }.

//
// IdP: endorsements for registered users and PIs.
//

defcon endorsePI(?PI) :-
  spec('Endorse a geni PI'),
  {
    geniUser(?PI).
    geniPI(?PI).
    label("pi/$PI").
  }.

defcon endorseUser(?User) :- 
  spec('Endorse a geni user'),
  {
    geniUser(?User).
    label("user/$User").
  }.

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
 
    label('pa/standard-project-membership') :- 
      spec('Standard project membership policy').
  }.

defcon delegateProjectMembership(?SubjectId, ?ProjectId, ?Delegatable) :-
  {
    delegateMember(?SubjectId, ?ProjectId, ?Delegatable).
    label("project membership delegation $SubjectId $ProjectId").
  }

//
// PA authZ policy for project creation.
//
defcon projectPolicySet() :-
  {
    approveProject(?Owner) :-
      geniPI(?Owner).
    label('pa/standard-project-operations').
  }

defcon createProjectSet(?SubjectId, ?ProjectId, ?ProjectMembershipRef) :-
  {
    owner(?SubjectId, ?ProjectId).
    project(?ProjectId, standard).
    link(?ProjectMembershipRef).
    label("project/$ProjectId").
  }.

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
 
    controlPrivilege(?Subject, ?Slice, instantiate, ?Delegator) :- 
      controls(?Subject, ?Slice, ?Delegator).
 
    controlPrivilege(?Subject, ?Slice, ?Priv, ?Delegatable) :- 
      delegatesControlPrivilege(?Delegator, ?Subject, ?Slice, ?Priv, ?Delegatable),
      controlPrivilege(?Delegator, ?Slice, ?Priv, true).

    delegatesControl(?Delegator, ?Subject, ?Slice, ?Delegatable) :-
      ?Delegator: delegateControl(?Subject, ?Slice, ?Delegatable).

    delegatesControlPrivilege(?Delegator, ?Subject, ?Slice, ?Priv, ?Delegatable) :-
      ?Delegator: delegatesControlPrivilege(?Subject, ?Slice, ?Priv, ?Delegatable).

    label('sa/standard-slice-control') :- 
      spec('Standard slice control policy').
  }.

defcon standardSliceDefaultPrivilegeSet() :-
  {
    controlPrivilege(?Subject, ?Slice, info, ?Delegatable) :-
      slice(?Slice, ?Project, standard),
      ?PA := rootId(?Project),
      projectAuthority(?PA),
      ?PA: project(?Project, standard),
      ?PA: memberPrivilege(?Subject, ?Project, info, ?Delegatable).
 
    controlPrivilege(?Subject, ?Slice, stop, true) :- 
      slice(?Slice, ?Project, standard),
      ?PA := rootId(??Project),
      projectAuthority(?PA),
      ?PA: project(?Project, standard),
      ?PA: owner(?Subject, ?Project).
 
    controlPrivilege(?Subject, ?Slice, info) :- 
      slice(?Slice, ?Project, standard),
      gmoc(?Subject).
 
    controlPrivilege(?Subject, ?Slice, stop) :- 
      slice(?Slice, ?Project, standard),
      gmoc(?Subject).
 
    label('sa/standard-slice-privilege') :- 
      spec('Standard slice default privilege policy').
  }.

//
// SA authZ policy for slice creation.
//
defcon slicePolicySet() :-
  {
    approveSlice(?Subject, ?Project) :-
      ?PA := rootPrincipal(?Project),
      projectAuthority(?PA),
      ?PA: project(?Project, standard),
      ?PA: memberPrivilege(?Subject, ?Project, instantiate),
      geniUser(?Subject).

    label('sa/standard-slice-operations').
  }.

//
// A newly approved slice is created as a set with links to its policy sets.
//

defcon createSliceSet(?SubjectId, ?SliceId, ?ProjectId, ?SliceControlRef, ?SlicePrivRef) :-
  {
    owner(?SubjectId, $?SliceId).
    slice(?SliceId, ?ProjectId, standard).
    link(?SliceControlRef).
    link(?SlicePrivRef).
    label("slice/$SliceId").
  }.

//
// Aggregate
//

defcon aggregatePolicySet() :- 
  {
     approveSliver(?Subject, ?Slice) :-
       ?SA := rootId(?Slice),
       ?SA: slice(?Slice, ?Project, standard),
       sliceAuthority(?SA), 
       ?SA: controlPrivilege(?Subject, ?Slice, instantiate, _),
       geniUser(?Subject).
 
     label('aggregate/standard-slice-operations').
  }.

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
  ?PolicyRef := label("pa/standard-project-operations"),
  {
    link($AnchorSet).
    link(?PolicyRef).
    link($BearerRef).
    approveProject($Subject)?
  }.

defguard createSlice(?ProjectId) :-
  spec("Guard for SliceAuthority.createSlice"),
  ?PolicyRef := label("sa/standard-slice-operations"),
  {
    link($AnchorSet).
    link(?PolicyRef).
    link($BearerRef). 
    approveSlice($Subject, ?ProjectId)?
  }.

defguard createSliver(?SliceId) :-
  spec("Guard for Aggregate.createSliver),
  ?PolicyRef := label("aggregate/standard-slice-operations"),
  {
    link($AnchorSet).
    link(?PolicyRef).
    link($BearerRef). 
    approveSliver($Subject, ?SliceId)?
  }.

//
// Local example
//

defcon fedEndorsementStub() :-
  ?E1 = endorseIdp($Self),
  ?E2 = endorsePA($Self),
  ?E3 = endorseSA($Self),
  ?E4 = endorseCP($Self),
  {
    link(?E1).
    link(?E2).
    link(?E3).
    link(?E4).
    label("geni/endorse-self-stub").
  }.

defcon anchorSet() :-
  ?FedStructureRef := label("geni/trust-structure"),
  ?FedUserRef := label("geni/registered-user"),
  ?FedEndorseRef := fedEndorsementStub(),
  {
    geniRoot($GeniRoot).
    link(?FedStructureRef).
    link(?FedUserRef).
    link(?FedEndorseRef).
  }.

defenv AnchorSet() :- anchorSet().
defenv GeniRoot() :- SomethingSomething or read it out of a file.

