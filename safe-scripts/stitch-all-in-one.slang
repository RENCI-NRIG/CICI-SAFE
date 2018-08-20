defenv Version() :- "set28".
defenv PolicyVersion() :- "policy28".

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
      geniPI(?Owner).
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

//End of geni


//
//Users
//We may just link the tag set in user' subject set
//

defcon makeTagSet(?Tag) :-
  spec('Construct the identity set for a tag'),
  {
    label("tags/$Tag($Version)").
  }.

defpost postTagSet(?Tag) :- [makeTagSet(?Tag)].

defcon addTokenToTagSet(?Token, ?Tag) :-
  spec("Add a link to a tag's ID set. Invoked after delegation to this tag"),
  {
    link($Token).
    label("tags/$Tag($Version)").
  }.

defpost updateTagSet(?Token, ?Tag) :- [addTokenToTagSet(?Token, ?Tag)].

//
//PA
//

defcon linkTagSetToProject(?ProjectId, ?TagSetRef) :-
  spec('link the project set to the tag set'),
  {
    link($TagSetRef).
    label('project/$ProjectId($Version)').
  }.

defpost postLinkTagSetToProject(?ProjectId, ?TagSetRef) :- [linkTagSetToProject(?ProjectId, ?TagSetRef)].


//
//SA
//

defcon linkTagSetToSlice(?SliceId, ?TagSetRef) :-
  spec('link the slice set to the tag set'),
  {
    link($TagSetRef).
    label('slice/$SliceId($Version)').
  }.

defpost postLinkTagSetToSlice(?SliceId, ?TagSetRef) :- [linkTagSetToSlice(?SliceId, ?TagSetRef)].

//Object tag set
//link the sets for tag assignemnts
//An object coudld be a slice or a project
defcon createObjectTagSet(?ObjectId, ?Tag) :-
  spec('tag set for an object'),
  {
    label('objectTags/$ObjectId/$Tag($Version)').
  }.

defpost postObjectTagSet(?ObjectId, ?Tag) :- [createObjectTagSet(?ObjectId, ?Tag)].

defcon updateObjectTagSet(?ObjectId, ?Tag, ?Token) :-
  {
    link($Token).
    label('objectTags/$ObjectId/$Tag($Version)').
  }.

defpost postUpdateObjectTagSet(?ObjectId, ?Tag, ?Token) :- [updateObjectTagSet(?ObjectId, ?Tag, ?Token)].

//
// Tag Authority 

defcon grantTagPriv(?Tag, ?User, ?Delegatable) :-
  spec('Delegate access for a tag to a user'),
  ?TagRef := label($Self, "tags/?Tag($Version)"),
  {
    grantTagPriv($Tag, $User, $Delegatable).
    link($TagRef).
    label("grantTagPriv/$Tag/$User($Version)").
    (* this link should be saved in the subject set of ?User *)
  }.

defpost postGrantTagPriv(?Tag, ?User, ?Delegatable) :- [grantTagPriv(?Tag, ?User, ?Delegatable)].

//The object could be a slice or a project
defcon grantTag(?Object, ?Tag) :-
  {
    assignTag($Object, $Tag).
    label("grantTag/$Object($Version)").
  }.

defpost postAssignTag(?Object, ?Tag) :- [grantTag(?Object, ?Tag)].

//
//SDX
//

defcon aclEntrySet() :-
  {
     label('aclEntry($Version)').
  }.

defpost postAclEntrySet() :- [aclEntrySet()].

defcon userAclEntry(?User) :-
  spec('SDX user access control list'),
  {
    userAclEntry($User).
    label('aclEntry($Version)').
  }.

defpost postUserAclEntry(?User):- [userAclEntry(?User)].

defcon userTagAclEntry(?Tag) :-
  spec('SDX user access control list'),
  {
    userTagAclEntry($Tag).
    label('aclEntry($Version)').
  }.

defpost postUserTagAclEntry(?Tag) :- [userTagAclEntry(?Tag)].

//TODO: project tag acl entry
defcon tagAclEntry(?Tag):-
  spec('acl set for project attributes'),
  {
    tagAclEntry($Tag).
    label('aclEntry($Version)').
  }.

defpost postTagAclEntry(?Tag)  :- [tagAclEntry(?Tag)].

defcon projectAuthorityAcl(?PA) :-
  spec('project authority acl'),
  {
    projectAuthority($PA).
    lable('aclEntry($Version)').
  }.

defpost postProjectAuthorityAcl(?PA) :-[projectAuthorityAcl(?PA)].
    
defcon stitchPolicy() :-
  spec('Local Policies on stitching authorization'),
  {
    //ACL list
    approveStitching(?User,?Slice):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      userAclEntry(?User).

    //User Attribute
    approveStitching(?User,?Slice):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      userTagAclEntry(?Tag),
      tagPrivilege(?Tag,?User).

    //Slice with attribute endorsed by third party
    approveStitching(?User, ?Slice):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      tagAclEntry(?Tag),
      tagAccess(?Tag,?Proj).

    //slice in a project by an accepted PA
    //adapted from the rule for SA to authorize a request to create a slice. with an additional line that check if the User is the owner of the userslice

    approveStitching(?User,?Slice):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      ?SA: slice(?Slice,?Proj),
      ?PA := rootPrincipal(?Proj),
      projectAuthority(?PA),
      ?PA: project(?Proj, standard).

    //project attribute
    approveStitching(?User,?Slice):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      ?SA: slice(?Slice,?Proj),
      tagAclEntry(?Tag),
      tagAccess(?Tag,?Proj).

    label('slice stitching policy($PolicyVersion)').
  }.

defpost postStitchPolicy() :- [stitchPolicy()].

//
//SDX
//

defcon tagPrivilegePolicy() :-
  spec('Local policies on the delegation of tag access'),
  {
    tagPrivilege(?Tag, ?User) :-
      tagPrivilege(?Tag, ?User, _).

    tagPrivilege(?Tag, ?User, ?Delegatable) :-
      ?Owner := rootPrincipal(?Tag), 
      ?Owner: grantTagPriv(?Tag, ?User, ?Delegatable).

    tagPrivilege(?Tag, ?User, ?Delegatable) :-
      ?Delegator: grantTagPriv(?Tag, ?User, ?Delegatable),
      tagPrivilege(?Tag, ?Delegator, true).
    
    tagAccess(?Tag, ?Object):-
      ?Delegator: assignTag(?Object, ?Tag),
      tagPrivilege(?Tag, ?Delegator).

    label('tag-access-policy($PolicyVersion)').
  }.

//definit tagPrivilegePolicy().

defpost postTagPrivilegePolicy() :- [tagPrivilegePolicy()].


//
//This is different from that in geni.slang, since here we use the sets to store attributes assigned to the slice and projec, 
//The owner of the sets are the project authority and slice owner.
//TODO: link project tag set here?


//NOTE: use double quotes for label
defguard authorizeStitch(?User, ?Slice):-
  spec('authorize stitching request based on user attribute or slice attribute'),
  ?StitchPolicyRef := label("slice stitching policy($PolicyVersion)"),
  ?AclSet := label("aclEntry($Version)"),
  ?UserSubjectSet := label(?User, "subject(?User)($Version)"),
  ?TagAccessPolicySet := label("tag-access-policy($PolicyVersion)"),
  {
    link($StitchPolicyRef).
    //link($BearerRef).
    link($UserSubjectSet).
    link($AclSet).
    link($TagAccessPolicySet).
    approveStitching($User, $Slice)?
  }.
