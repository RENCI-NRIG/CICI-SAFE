import("./geni_fine.slang").

//
//Users
//We may just link the tag set in user" subject set
//
defenv RpkiRoot() :- "FcHzu-wG1BEHYBljyKf-4l9H3mFqsD2IOObzrhq2KcE=".

defcon makeTagSet(?Tag) :-
  spec("Construct the identity set for a tag"),
  {
    label("tags/$Tag($Version)").
  }.

defpost postTagSet(?Tag) :- [makeTagSet(?Tag)].

defcon makeTagSubjectSet() :-
  spec("Construct the identity set for a all tags"),
  {
    label("tags($Version)").
  }.

defpost initTagSubjectSet() :- [makeTagSubjectSet()].

defcon addTokenToTagSet(?Token, ?Tag) :-
  spec("Add a link to a tag's ID set. Invoked after delegation to this tag"),
  {
    link($Token).
    label("tags/$Tag($Version)").
  }.

defcon addTokenToTagSubjectSet(?Token) :-
  spec("Add a link to a tag's ID set. Invoked after delegation to this tag"),
  {
    link($Token).
    label("tags($Version)").
  }.

defpost updateTagSet(?Token, ?Tag) :- [addTokenToTagSet(?Token, ?Tag), addTokenToTagSubjectSet(?Token), addTokenToSubjectSet(?Token)].

//
//PA
//

defcon linkTagSetToProject(?ProjectId, ?TagSetRef) :-
  spec("link the project set to the tag set"),
  {
    link($TagSetRef).
    label("project/$ProjectId($Version)").
  }.

defpost postLinkTagSetToProject(?ProjectId, ?TagSetRef) :- [linkTagSetToProject(?ProjectId, ?TagSetRef)].


//
//SA
//

defcon linkTagSetToSlice(?SliceId, ?TagSetRef) :-
  spec("link the slice set to the tag set"),
  {
    link($TagSetRef).
    label("slice/$SliceId($Version)").
  }.

defpost postLinkTagSetToSlice(?SliceId, ?TagSetRef) :- [linkTagSetToSlice(?SliceId, ?TagSetRef)].

//Object tag set
//link the sets for tag assignemnts
//An object coudld be a slice or a project
defcon createObjectTagSet(?ObjectId, ?Tag) :-
  spec("tag set for an object"),
  {
    label("objectTags/$ObjectId/$Tag($Version)").
  }.

defpost postObjectTagSet(?ObjectId, ?Tag) :- [createObjectTagSet(?ObjectId, ?Tag)].

defcon updateObjectTagSet(?ObjectId, ?Tag, ?Token) :-
  {
    link($Token).
    label("objectTags/$ObjectId/$Tag($Version)").
  }.

defpost postUpdateObjectTagSet(?ObjectId, ?Tag, ?Token) :- [updateObjectTagSet(?ObjectId, ?Tag, ?Token)].

//
// Tag Authority 

defcon grantTagPriv(?User, ?Tag, ?Delegatable) :-
  spec("Delegate access for a tag to a user"),
  ?TagRef := label("tags/?Tag($Version)"),
  {
    grantTagPriv($Tag, $User, $Delegatable).
    link($TagRef).
    label("grantTagPriv/$Tag/$User($Version)").
    (* this link should be saved in the subject set of ?User *)
  }.

defpost postGrantTagPriv(?User, ?Tag, ?Delegatable) :- [grantTagPriv(?User, ?Tag, ?Delegatable)].

//The object could be a slice or a project
defcon grantTag(?Object, ?Tag) :-
  ?TagRef := label("tags/?Tag($Version)"),
  {
    assignTag($Object, $Tag).
    link($TagRef).
    label("grantTag/$Object($Version)").
  }.

defpost postAssignTag(?Object, ?Tag) :- [grantTag(?Object, ?Tag)].

//
//SDX
//

defcon aclEntrySet() :-
  {
     label("aclEntry($Version)").
  }.

defpost postAclEntrySet() :- [aclEntrySet()].

defcon userAclEntry(?User) :-
  spec("SDX user access control list"),
  {
    userAclEntry($User).
    label("userAclEntry($Version)").
  }.

defpost postUserAclEntry(?User):- [userAclEntry(?User)].

defcon userTagAclEntry(?Tag) :-
  spec("SDX user access control list"),
  {
    userTagAclEntry($Tag).
    label("userTagAclEntry($Version)").
  }.

defpost postUserTagAclEntry(?Tag) :- [userTagAclEntry(?Tag)].

defcon sliceTagAclEntry(?Tag):-
  spec("acl set for project attributes"),
  {
    sliceTagAclEntry($Tag).
    label("sliceTagAclEntry($Version)").
  }.

defpost postSliceTagAclEntry(?Tag)  :- [sliceTagAclEntry(?Tag)].

defcon tagAclEntry(?Tag):-
  spec("acl set for project attributes"),
  {
    projectTagAclEntry($Tag).
    label("projectTagAclEntry($Version)").
  }.

defpost postTagAclEntry(?Tag)  :- [tagAclEntry(?Tag)].

defcon projectIDAcl(?PID) :-
  spec("project authority acl"),
  {
    projectIDAclEntry($PID).
    label("projectIDAclEntry($Version)").
  }.

defpost postProjectIDAcl(?PID) :-[projectIDAcl(?PID)].

defcon ownPrefixPolicy():-
  {
    ownPrefix(?Prcpl,?Prefix):-
      $RpkiRoot: allocate(?Prcpl,?Prefix).

    ownPrefix(?Prcpl,?Prefix):-
      ?UpStream: allocate(?Prcpl,?Prefix),
      ownPrefix(?UpStream,?PrefixSet),
      ?Prefix <: ?PrefixSet.
    label("own prefix policy($PolicyVersion)").
  }.

definit ownPrefixPolicy().

defpost postOwnPrefixPolicy() :- [ownPrefixPolicy()].

defcon ipAllocate(?Prcpl,?Prefix,?DlgPrefix) :-
  ?IPSetToken := label("Token for IP allocation/?DlgPrefix($Version)"),
  {
    link($IPSetToken).
    allocate($Prcpl,$Prefix).
    label("IP allocation/$Prcpl($Version)").
  }.

defpost postIPAllocate(?Prcpl,?Prefix,?DlgPrefix) :-[ipAllocate(?Prcpl,?Prefix,?DlgPrefix)].

defcon makeIPTokenSet(?DlgPrefix) :-
  {
    label("Token for IP allocation/$DlgPrefix($Version)").
  }.

defpost postMakeIPTokenSet(?DlgPrefix):- [makeIPTokenSet(?DlgPrefix)].

defcon addDlgToken(?Token,?Prefix):-
  spec("link a token for allocated IP range"),
  {
    link($Token).
    label("Token for IP allocation/$Prefix($Version)").
  }.

defpost postDlgToken(?Token,?Prefix) :- [addDlgToken(?Token,?Prefix), addTokenToSubjectSet(?Token)].

//
//SDX
//

defcon connectivityPolicy() :-
  spec("SDX policies for network connection between customers"),
  {
    authZByUserAttr(?Alice, ?Bob, ?IPa, ?IPb):-
        ownPrefix(?Alice, ?IPa),
        ownPrefix(?Bob, ?IPb),
        ?Alice: allowConnectionByUserAttr(?Bob),
        ?Bob: allowConnectionByUserAttr(?Alice).

    authZByPID(?Alice, ?Bob, ?IPa, ?IPb):-
        ownPrefix(?Alice, ?IPa),
        ownPrefix(?Bob, ?IPb),
        ?Alice: allowConnectionByPID(?Bob),
        ?Bob: allowConnectionByPID(?Alice).

    authZByProjectAttr(?Alice, ?Bob, ?IPa, ?IPb):-
        ownPrefix(?Alice, ?IPa),
        ownPrefix(?Bob, ?IPb),
        ?Alice: allowConnectionByProjectAttr(?Bob),
        ?Bob: allowConnectionByProjectAttr(?Alice).

    label("sdx-connectivity-policy($PolicyVersion)").
  }.

definit connectivityPolicy().

defcon authZPolicy() :-
    ?PrefixPolicy := label("own prefix policy($PolicyVersion)"),
    ?ConnectPolicy := label("sdx-connectivity-policy($PolicyVersion)"),
  {
    link($PrefixPolicy).
    link($ConnectPolicy).
    label("authZPolicy($PolicyVersion)").
  }.

definit authZPolicy().

defcon stitchPolicy() :-
  spec("Local Policies on stitching authorization"),
  {

    ////ACL list
    approveStitchByUID(?User,?Slice):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      userAclEntry(?User).

    //User Attribute
    //TODO move user to the first place, may reorder usertag entry and tag priv
    approveStitchByUserAttr(?User,?Slice, ?Tag):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      userTagAclEntry(?Tag),
      tagPrivilege(?Tag,?User).

    //Slice with attribute endorsed by third party
    approveStitchBySliceAttr(?User, ?Slice, ?Tag):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      sliceTagAclEntry(?Tag),
      tagAccess(?Tag,?Slice).

    //slice in a project by an accepted PI
    //adapted from the rule for SA to authorize a request to create a slice. with an additional line that check if the User is the owner of the userslice

    approveStitchByPID(?User,?Slice):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      ?SA: slice(?Slice,?Proj, standard),
      projectIDAclEntry(?Proj).

    //project attribute
    approveStitchByProjectAttr(?User,?Slice, ?Tag):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      ?SA: slice(?Slice,?Proj, standard),
      projectTagAclEntry(?Tag),
      tagAccess(?Tag,?Proj).
    
    approveChameleonStitchByUID(?User, ?Stitchport, ?Vlan):-
      userAclEntry(?User),
      ?User: stitchChameleon(?Stitchport, ?Vlan).

    label("slice stitching policy($PolicyVersion)").
  }.

defcon chameleonStitchRequest(?Stitchport,?Vlan):-
  spec('customer post a chameleon sttich request'),
  {
    stitchChameleon($Stitchport,$Vlan).
    label("stitch request($Version)").
  }.

defpost postChameleonStitchRequest(?Stitchport,?Vlan):-[chameleonStitchRequest(?Stitchport,?Vlan)].

definit stitchPolicy().

defpost postStitchPolicy() :- [stitchPolicy()].

defcon tagPrivilegePolicy() :-
  spec("Local policies on the delegation of tag access"),
  {
    tagPrivilege(?Tag, ?User) :-
      tagPrivilege(?Tag, ?User, _).

    tagPrivilege(?Tag, ?User, true) :-
      ?User := rootPrincipal(?Tag).

    tagPrivilege(?Tag, ?User, ?Delegatable) :-
      ?Owner := rootPrincipal(?Tag), 
      ?Owner: grantTagPriv(?Tag, ?User, ?Delegatable).

    tagPrivilege(?Tag, ?User, ?Delegatable) :-
      ?Delegator: grantTagPriv(?Tag, ?User, ?Delegatable),
      tagPrivilege(?Tag, ?Delegator, true).
    
    tagAccess(?Tag, ?Object):-
      ?Delegator: assignTag(?Object, ?Tag),
      tagPrivilege(?Tag, ?Delegator).

    label("tag-access-policy($PolicyVersion)").
  }.

definit tagPrivilegePolicy().

defpost postTagPrivilegePolicy() :- [tagPrivilegePolicy()].

//
//Customer policies
//

defcon customerConnectionPolicy() :-
  spec("customer Policies on network connection"),
  {
    //ACL list with statement

    //User Attribute
    allowConnectionByUserAttr(?User):-
      userTagAclEntry(?Tag),
      tagPrivilege(?Tag,?User).
    
    //user in a project by an accepted PI

    allowConnectionByPID(?User):-
      projectIDAclEntry(?Proj),
      ?PA:= rootPrincipal(?Proj),
      ?PA: member(?User, ?Proj).

    //User in a project with attribute
    //NOTE: projectAth
    allowConnectionByProjectAttr(?User):-
      projectTagAclEntry(?Tag),
      tagAccess(?Tag, ?Proj),
      ?PA := rootPrincipal(?Proj),
      ?PA: member(?User, ?Proj).

    label("customer-connection-policy($PolicyVersion)").
  }.

defpost postCustomerConnectionPolicy() :- [customerConnectionPolicy()].

defcon customerPolicy() :-
  spec("Customer link all its policies to a set"),
  ?PolicySet := label("customer-connection-policy($PolicyVersion)"),
  ?TagPrivSet := label("tag-access-policy($PolicyVersion)"),
  ?SubjectSet := label("subject($Self)($Version)"),
  {
    link($PolicySet).
    link($TagPrivSet).
    link($SubjectSet).
    label("customer-policy($PolicyVersion)").
  }.

defpost postCustomerPolicy() :-[customerPolicy()].

defguard whoami() :-
  spec("Query principal id"),
  {
    principalId($Self).
    principalId(?X)?
  }.

//NOTE: use double quotes for label
defguard authorizeStitchByUID(?User, ?Slice):-
  spec('authorize stitching request based on user attribute or slice attribute'),
  ?StitchPolicyRef := label("slice stitching policy($PolicyVersion)"),
  ?AclSet := label("userAclEntry($Version)"),
  ?UserSubjectSet := label(?User, "subject(?User)($Version)"),
  ?TagAccessPolicySet := label("tag-access-policy($PolicyVersion)"),
  {
    link($StitchPolicyRef).
    //link($BearerRef).
    link($UserSubjectSet).
    link($AclSet).
    link($TagAccessPolicySet).
    approveStitchByUID($User, $Slice)?
  }.

//NOTE: use double quotes for label
defguard authorizeChameleonStitchByUID(?User, ?StitchPort,?Vlan):-
  spec('authorize stitching request based on user attribute or slice attribute'),
  ?StitchPolicyRef := label("slice stitching policy($PolicyVersion)"),
  ?AclSet := label("userAclEntry($Version)"),
  ?UserSubjectSet := label(?User, "subject(?User)($Version)"),
  ?TagAccessPolicySet := label("tag-access-policy($PolicyVersion)"),
  ?RequestToken := label(?User,"stitch request($Version)"),
  {
    link($StitchPolicyRef).
    //link($BearerRef).
    link($UserSubjectSet).
    link($AclSet).
    link($TagAccessPolicySet).
    link($RequestToken).
    approveChameleonStitchByUID($User, $StitchPort, $Vlan)?
  }.

defguard authorizeStitchByUserAttr(?User, ?Slice):-
  spec('authorize stitching request based on user attribute or slice attribute'),
  ?StitchPolicyRef := label("slice stitching policy($PolicyVersion)"),
  ?AclSet := label("userTagAclEntry($Version)"),
  ?UserSubjectSet := label(?User, "subject(?User)($Version)"),
  ?TagAccessPolicySet := label("tag-access-policy($PolicyVersion)"),
  {
    link($StitchPolicyRef).
    //link($BearerRef).
    link($UserSubjectSet).
    link($AclSet).
    link($TagAccessPolicySet).
    approveStitchByUserAttr($User, $Slice)?
  }.

defguard authorizeStitchBySliceAttr(?User, ?Slice):-
  spec('authorize stitching request based on user attribute or slice attribute'),
  ?StitchPolicyRef := label("slice stitching policy($PolicyVersion)"),
  ?AclSet := label("sliceTagAclEntry($Version)"),
  ?UserSubjectSet := label(?User, "subject(?User)($Version)"),
  ?TagAccessPolicySet := label("tag-access-policy($PolicyVersion)"),
  {
    link($StitchPolicyRef).
    //link($BearerRef).
    link($UserSubjectSet).
    link($AclSet).
    link($TagAccessPolicySet).
    approveStitchBySliceAttr($User, $Slice)?
  }.

defguard authorizeStitchByPA(?User, ?Slice):-
  spec('authorize stitching request based on user attribute or slice attribute'),
  ?StitchPolicyRef := label("slice stitching policy($PolicyVersion)"),
  ?AclSet := label("projectAuthorityAclEntry($Version)"),
  ?UserSubjectSet := label(?User, "subject(?User)($Version)"),
  ?TagAccessPolicySet := label("tag-access-policy($PolicyVersion)"),
  {
    link($StitchPolicyRef).
    //link($BearerRef).
    link($UserSubjectSet).
    link($AclSet).
    link($TagAccessPolicySet).
    approveStitchByPA($User, $Slice)?
  }.

defguard authorizeStitchByProjectAttr(?User, ?Slice):-
  spec('authorize stitching request based on user attribute or slice attribute'),
  ?StitchPolicyRef := label("slice stitching policy($PolicyVersion)"),
  ?AclSet := label("projectTagAclEntry($Version)"),
  ?UserSubjectSet := label(?User, "subject(?User)($Version)"),
  ?TagAccessPolicySet := label("tag-access-policy($PolicyVersion)"),
  {
    link($StitchPolicyRef).
    //link($BearerRef).
    link($UserSubjectSet).
    link($AclSet).
    link($TagAccessPolicySet).
    approveStitchByProjectAttr($User, $Slice)?
  }.


defguard authZByPI(?Alice, ?IPa, ?Bob, ?IPb) :-
  spec('authorize network connection between customer Alice and Bob'),
  ?AliceConPolicyRef := label(?Alice, "customer-connection-policy($PolicyVersion)"),
  ?AliceTagPolicyRef := label(?Alice, "tag-access-policy($PolicyVersion)"),
  ?AliceAclRef := label(?Alice, "projectAuthorityAclEntry($Version)"),
  ?AliceSubjectSet := label(?Alice, "subject(?Alice)($Version)"),
  ?BobConPolicyRef := label(?Bob, "customer-connection-policy($PolicyVersion)"),
  ?BobTagPolicyRef := label(?Bob, "tag-access-policy($PolicyVersion)"),
  ?BobAclRef := label(?Bob, "projectAuthorityAclEntry($Version)"),
  ?BobSubjectSet := label(?Bob, "subject(?Bob)($Version)"),
  ?ConnectPolicy := label("sdx-connectivity-policy($PolicyVersion)"),
  ?OwnPrefixPolicySet := label("own prefix policy($PolicyVersion)"),
  {
     link($AliceConPolicyRef).
     link($AliceTagPolicyRef).
     link($AliceAclRef).
     link($AliceSubjectSet).
     link($BobConPolicyRef).
     link($BobTagPolicyRef).
     link($BobAclRef).
     link($BobSubjectSet).
     link($ConnectPolicy).
     link($OwnPrefixPolicySet).
     authZByPI($Alice, $Bob, $IPa, $IPb)?
  }.

defguard authZByProjectAttr(?Alice, ?IPa, ?Bob, ?IPb) :-
  spec('authorize network connection between customer Alice and Bob'),
  ?AliceConPolicyRef := label(?Alice, "customer-connection-policy($PolicyVersion)"),
  ?AliceTagPolicyRef := label(?Alice, "tag-access-policy($PolicyVersion)"),
  ?AliceAclRef := label(?Alice, "projectTagAclEntry($Version)"),
  ?AliceSubjectSet := label(?Alice, "subject(?Alice)($Version)"),
  ?BobConPolicyRef := label(?Bob, "customer-connection-policy($PolicyVersion)"),
  ?BobTagPolicyRef := label(?Bob, "tag-access-policy($PolicyVersion)"),
  ?BobAclRef := label(?Bob, "projectTagAclEntry($Version)"),
  ?BobSubjectSet := label(?Bob, "subject(?Bob)($Version)"),
  ?ConnectPolicy := label("sdx-connectivity-policy($PolicyVersion)"),
  ?OwnPrefixPolicySet := label("own prefix policy($PolicyVersion)"),
  {
     link($AliceConPolicyRef).
     link($AliceTagPolicyRef).
     link($AliceAclRef).
     link($AliceSubjectSet).
     link($BobConPolicyRef).
     link($BobTagPolicyRef).
     link($BobAclRef).
     link($BobSubjectSet).
     link($ConnectPolicy).
     link($OwnPrefixPolicySet).
     authZByProjectAttr($Alice, $Bob, $IPa, $IPb)?
  }.

defguard authZByUserAttr(?Alice, ?IPa, ?Bob, ?IPb) :-
  spec('authorize network connection between customer Alice and Bob'),
  ?AliceConPolicyRef := label(?Alice, "customer-connection-policy($PolicyVersion)"),
  ?AliceTagPolicyRef := label(?Alice, "tag-access-policy($PolicyVersion)"),
  ?AliceAclRef := label(?Alice, "userTagAclEntry($Version)"),
  ?AliceSubjectSet := label(?Alice, "subject(?Alice)($Version)"),
  ?BobConPolicyRef := label(?Bob, "customer-connection-policy($PolicyVersion)"),
  ?BobTagPolicyRef := label(?Bob, "tag-access-policy($PolicyVersion)"),
  ?BobAclRef := label(?Bob, "userTagAclEntry($Version)"),
  ?BobSubjectSet := label(?Bob, "subject(?Bob)($Version)"),
  ?ConnectPolicy := label("sdx-connectivity-policy($PolicyVersion)"),
  ?OwnPrefixPolicySet := label("own prefix policy($PolicyVersion)"),
  {
     link($AliceConPolicyRef).
     link($AliceTagPolicyRef).
     link($AliceAclRef).
     link($AliceSubjectSet).
     link($BobConPolicyRef).
     link($BobTagPolicyRef).
     link($BobAclRef).
     link($BobSubjectSet).
     link($ConnectPolicy).
     link($OwnPrefixPolicySet).
     authZByUserAttr($Alice, $Bob, $IPa, $IPb)?
  }.

defguard authorizeOwnPrefix(?User,?Prefix) :-
  spec('authorize if user owns prefix'),
  ?UserSubjectSet := label(?User, "subject(?User)($Version)"),
  ?OwnPrefixPolicy := label("own prefix policy($PolicyVersion)"),
  {
     link($UserSubjectSet).
     link($OwnPrefixPolicy).
     ownPrefix($User, $Prefix)?
  }.
