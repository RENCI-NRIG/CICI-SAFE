import('/home/yaoyj11/project/uber-safe/safe-apps/safe-network/sdx/geni-test-new.slang').

//
//Users
//We may just link the tag set in user' subject set
//
defenv RpkiRoot() :- u'FcHzu-wG1BEHYBljyKf-4l9H3mFqsD2IOObzrhq2KcE'.

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
  ?TagRef := label("tags/?Tag($Version)"),
  {
    grantTagPriv($Tag, $User, $Delegatable).
    link($TagRef).
    label("grantTagPriv/$Tag/$User($Version)").
    (* this link should be saved in the subject set of ?User *)
  }.

defpost postGrantTagPriv(?Tag, ?User, ?Delegatable) :- [grantTagPriv(?Tag, ?User, ?Delegatable)].

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

defcon sliceTagAclEntry(?Tag):-
  spec('acl set for project attributes'),
  {
    sliceTagAclEntry($Tag).
    label('aclEntry($Version)').
  }.

defpost postSliceTagAclEntry(?Tag)  :- [sliceTagAclEntry(?Tag)].

defcon tagAclEntry(?Tag):-
  spec('acl set for project attributes'),
  {
    projectTagAclEntry($Tag).
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

defpost postDlgToken(?Token,?Prefix) :- [addDlgToken(?Token,?Prefix)].

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

    authZByPI(?Alice, ?Bob, ?IPa, ?IPb):-
        ownPrefix(?Alice, ?IPa),
        ownPrefix(?Bob, ?IPb),
        ?Alice: allowConnectionByPI(?Bob),
        ?Bob: allowConnectionByPI(?Alice).

    authZByProjectAttr(?Alice, ?Bob, ?IPa, ?IPb):-
        ownPrefix(?Alice, ?IPa),
        ownPrefix(?Bob, ?IPb),
        ?Alice: allowConnectionByProjectAttr(?Bob),
        ?Bob: allowConnectionByProjectAttr(?Alice).

    label('sdx-connectivity-policy($PolicyVersion)').
  }.

definit connectivityPolicy().

defcon stitchPolicy() :-
  spec('Local Policies on stitching authorization'),
  {
    ////ACL list
    approveStitchByUID(?User,?Slice):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      userAclEntry(?User).

    //User Attribute
    approveStitchByUserAttr(?User,?Slice):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      userTagAclEntry(?Tag),
      tagPrivilege(?Tag,?User).

    //Slice with attribute endorsed by third party
    approveStitchBySliceAttr(?User, ?Slice):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      sliceTagAclEntry(?Tag),
      tagAccess(?Tag,?Slice).

    //slice in a project by an accepted PI
    //adapted from the rule for SA to authorize a request to create a slice. with an additional line that check if the User is the owner of the userslice

    approveStitchByPA(?User,?Slice):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      ?SA: slice(?Slice,?Proj, standard),
      ?PA := rootPrincipal(?Proj),
      projectAuthority(?PA),
      ?PA: project(?Proj, standard).

    //project attribute
    approveStitchByProjectAttr(?User,?Slice):-
      ?SA :=rootPrincipal(?Slice),
      ?SA: controlPrivilege(?User,?Slice,stitch,_),
      ?SA: slice(?Slice,?Proj, standard),
      projectTagAclEntry(?Tag),
      tagAccess(?Tag,?Proj).

    label('slice stitching policy($PolicyVersion)').
  }.

definit stitchPolicy().

defpost postStitchPolicy() :- [stitchPolicy()].

defcon tagPrivilegePolicy() :-
  spec('Local policies on the delegation of tag access'),
  {
    tagPrivilege(?Tag, ?User) :-
      tagPrivilege(?Tag, ?User, _).

    tagPrivilege(?Tag, ?User, true) :-
      ?Owner := rootPrincipal(?Tag).

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

definit tagPrivilegePolicy().

defpost postTagPrivilegePolicy() :- [tagPrivilegePolicy()].

//
//Customer policies
//

defcon customerConnectionPolicy() :-
  spec('customer Policies on network connection'),
  {
    //ACL list with statement

    //User Attribute
    allowConnectionByUserAttr(?User):-
      userTagAclEntry(?Tag),
      tagPrivilege(?Tag,?User).
    
    //user in a project by an accepted PI

    allowConnectionByPI(?User):-
      projectAuthority(?PA),
      ?PA: project(?Proj, standard),
      ?PA: member(?User, ?Proj).

    //User in a project with attribute
    allowConnectionByProjectAttr(?User):-
      projectTagAclEntry(?Tag),
      tagAccess(?Tag,?Proj),
      ?PA := rootPrincipal(?Proj),
      ?PA: member(?User, ?Proj).

    label('customer-connection-policy($PolicyVersion)').
  }.

defpost postCustomerConnectionPolicy() :- [customerConnectionPolicy()].


defcon customerPolicy() :-
  spec('Customer link all its policies to a set'),
  ?PolicySet := label("(customer-connection-policy($PolicyVersion)"),
  ?TagPrivSet := label("tag-access-policy($PolicyVersion)"),
  {
    link($PolicySet).
    link($TagPrivSet).
    label('customer-policy($PolicyVersion)').
  }.

defpost postCustomerPolicy() :-[customerPolicy()].

//
//This is different from that in geni.slang, since here we use the sets to store attributes assigned to the slice and projec, 
//The owner of the sets are the project authority and slice owner.
//TODO: link project tag set here?


//NOTE: use double quotes for label
defguard authorizeStitchByUID(?User, ?Slice):-
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
    approveStitchByUID($User, $Slice)?
  }.

defguard authorizeStitchByUserAttr(?User, ?Slice):-
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
    approveStitchByUserAttr($User, $Slice)?
  }.

defguard authorizeStitchBySliceAttr(?User, ?Slice):-
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
    approveStitchBySliceAttr($User, $Slice)?
  }.

defguard authorizeStitchByPA(?User, ?Slice):-
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
    approveStitchByPA($User, $Slice)?
  }.

defguard authorizeStitchByProjectAttr(?User, ?Slice):-
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
    approveStitchByProjectAttr($User, $Slice)?
  }.

defguard authorizeConnect(?Alice, ?IPa, ?Bob, ?IPb) :-
  spec('authorize network connection between customer Alice and Bob'),
  ?AlicePolicyRef := label(?Alice, "customer-policy($PolicyVersion)"),
  ?AliceAclRef := label(?Alice, "aclEntry($Version)"),
  ?AliceSubjectSet := label(?Alice, "subject(?Alice)($Version)"),
  ?BobPolicyRef := label(?Bob, "customer-policy($PolicyVersion)"),
  ?BobAclRef := label(?Bob, "aclEntry($Version)"),
  ?BobSubjectSet := label(?Bob, "subject(?Bob)($Version)"),
  ?ConnectPolicy := label("sdx-connectivity-policy($PolicyVersion)"),
  ?OwnPrefixPolicySet := label("own prefix policy($PolicyVersion)"),
  {
     link($AlicePolicyRef).
     link($AliceAclRef).
     link($AliceSubjectRef).
     link($BobPolicyRef).
     link($BobAclRef).
     link($BobSubjectRef).
     link($ConnectPolicy).
     link($PolicySet).
     connect($Alice, $IPa, $Bob, $IPb)?
  }.

defguard authZByPI(?Alice, ?IPa, ?Bob, ?IPb) :-
  spec('authorize network connection between customer Alice and Bob'),
  ?AliceConPolicyRef := label(?Alice, "customer-connection-policy($PolicyVersion)"),
  ?AliceTagPolicyRef := label(?Alice, "tag-access-policy($PolicyVersion)"),
  ?AliceAclRef := label(?Alice, "aclEntry($Version)"),
  ?AliceSubjectSet := label(?Alice, "subject(?Alice)($Version)"),
  ?BobConPolicyRef := label(?Bob, "customer-connection-policy($PolicyVersion)"),
  ?BobTagPolicyRef := label(?Bob, "tag-access-policy($PolicyVersion)"),
  ?BobAclRef := label(?Bob, "aclEntry($Version)"),
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
  ?AliceAclRef := label(?Alice, "aclEntry($Version)"),
  ?AliceSubjectSet := label(?Alice, "subject(?Alice)($Version)"),
  ?BobConPolicyRef := label(?Bob, "customer-connection-policy($PolicyVersion)"),
  ?BobTagPolicyRef := label(?Bob, "tag-access-policy($PolicyVersion)"),
  ?BobAclRef := label(?Bob, "aclEntry($Version)"),
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
  ?AliceAclRef := label(?Alice, "aclEntry($Version)"),
  ?AliceSubjectSet := label(?Alice, "subject(?Alice)($Version)"),
  ?BobConPolicyRef := label(?Bob, "customer-connection-policy($PolicyVersion)"),
  ?BobTagPolicyRef := label(?Bob, "tag-access-policy($PolicyVersion)"),
  ?BobAclRef := label(?Bob, "aclEntry($Version)"),
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
