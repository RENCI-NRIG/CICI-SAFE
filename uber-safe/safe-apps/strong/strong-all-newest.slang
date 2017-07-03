//defenv Selfie() :-
//  spec('Load the principal keypair'),
//  principal($1).
//  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/registry.pem'). *)

//defenv RootDir() :- 'IBpC0ljPorTuO85iJ-9GIxkXyDlOHAfFwcKfGtj-h2U:root'.
//defenv RootDir() :- 'hBZy2DLtG7qF7CxdZNWusPKdDz6LbA3lBDFIBDGbHTo:root'.
defenv RootDir() :- 'CdvVx7fzo6TFsNSjf2srrz0pzGnBMxdq0bSM-7aelOo:root'.

//
// Identity set (apply to all participants)
//

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defcon makeMySubjectSet() :-
  spec("Construct a subject set for self"),
  {
    link($Self).
    label("subject($Self)").
  }.

defcon addTokenToSubjectSet(?Token) :-
  spec("Add a token to the user's subject set."),
  {
    link($Token).
    label("subject($Self)").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postSubjectSet() :- [makeMySubjectSet()].

defpost updateSubjectSet(?Token) :- [addTokenToSubjectSet(?Token)].

//
// Group Authority 
//

defcon makeGroupSet(?Group) :-
  spec('Construct a set for a group'),
  {
    label("groups/$Group").
  }.

defpost postGroupSet(?Group) :- [makeGroupSet(?Group)].

defcon addTokenToGroupSet(?Token, ?Group) :-
  spec("Add a link to a group's ID set. Invoked on delegation to this group"),
  {
    link($Token).
    label("groups/$Group").
  }.

defpost updateGroupSet(?Token, ?Group) :- [addTokenToGroupSet(?Token, ?Group)].

defcon addGroupMember(?Group, ?User, ?Delegatable) :-
  spec('Add a user into a group'),
  ?GroupRef := label($Self, "groups/?Group"),
  {
    groupMember($Group, $User, $Delegatable).
    link($GroupRef).
    label("groupMember/$Group/$User").     (* this link should be saved in the subject set of ?User *)
  }.

defpost postGroupMember(?Group, ?User, ?Delegatable) :- [addGroupMember(?Group, ?User, ?Delegatable)].

defcon delegateGroup(?Group, ?SubGroup, ?Delegatable) :-
  spec('Delegate administration to another group'), (* Membership is granted if membership of sub-group is granted *)
  ?GroupRef := label($Self, "groups/?Group"),
  {
    nestGroup($Group, $SubGroup, $Delegatable).
    link($GroupRef).
    label("delegateGroup/$Group/$SubGroup").  (* this link should be saved in the ID set of ?SubGroup *)
  }.

defpost postGroupDelegation(?Group, ?SubGroup, ?Delegatable) :- [delegateGroup(?Group, ?SubGroup, ?Delegatable)].


//
// User (group member)
//

defcon delegateGroupMembership(?User, ?Group, ?Delegatable) :-
  spec('Delegate group membership to another user'),
  ?SubjectSetRef := label("subject($Self)"),
  {
    delegateMembership($User, $Group, $Delegatable).
    link($SubjectSetRef).
    label("membershipDelegation/$Group/$User").     (* this link should be saved in the subject set of ?User *)
  }.

defpost postMembershipDelegation(?User, ?Group, ?Delegatable) :- [delegateGroupMembership(?User, ?Group, ?Delegatable)].


//
// Naming authority
//

defcon makeObjectSet(?Scid) :-
  spec("Construct a set for object ?Scid"),
  {
    label("$Scid").
  }.

defcon addTokenToObjectSet(?Token, ?Scid) :-
  spec("Add a link to an object set"),
  {
    link($Token).
    label("$Scid").
  }.

defpost postObjectSet(?Scid) :- [makeObjectSet(?Scid)].

defpost updateObjectSet(?Token, ?Scid) :- [addTokenToObjectSet(?Token, ?Scid)].

defcon localObject(?ObjectName, ?Uuid) :- 
  spec('Add a local object'),
  {
    localObject($ObjectName, "$Self:$Uuid").
    label("localObjects/$ObjectName").
  }.

defpost postLocalObject(?ObjectName, ?Uuid) :- [localObject(?ObjectName, ?Uuid)].

defcon createNameEntry(?ObjectName, ?ChildScid, ?ParentScid) :-
  spec('Create a name entry'),
  ?ParentToken := label(?ParentScid),
  {
    link($ParentToken).
    nameObject($ObjectName, $ChildScid, $ParentScid).
    nameObject2($ParentScid, $ObjectName, $ChildScid).
    label("$ParentScid/$ObjectName").
  }.

defpost postNameDelegation(?ObjectName, ?ChildScid, ?ParentScid) :- [createNameEntry(?ObjectName, ?ChildScid, ?ParentScid)].


defcon grantAccess(?Group, ?DirScid) :-
  spec('Grant group access to directory'),
  {
    accessToDir($Group, $DirScid).
    label("$DirScid").
  }.

defpost postDirectoryAccess(?Group, ?DirScid) :- [grantAccess(?Group, ?DirScid)].


//
// Guard
//

defcon memberSet() :-
  spec('Local policies on group membership'),
  {
    membership(?Group, ?User) :-
      membership(?Group, ?User, _).
  
   //
   // If the group root has the membership automatically, 
   // update the root's subject set    
   // membership(?Group, ?User, true) :-
   //   ?GRoot := rootPrincipal(?Group),
   //   ?GRoot = ?User.

    membership(?Group, ?User, ?Delegatable) :-
      ?GRoot := rootPrincipal(?Group), 
      ?GRoot: groupMember(?Group, ?User, ?Delegatable).
    
    membership(?Group, ?User, ?Delegatable) :-
      ?Delegator: delegateMembership(?User, ?Group, ?Delegatable),
      membership(?Group, ?Delegator, true).

    membership(?Group, ?User, ?Delegatable) :-
      ?GRoot := rootPrincipal(?Group),
      ?GRoot: nestGroup(?Group, ?ToGroup, true), 
      membership(?ToGroup, ?User, ?Delegatable).

    membership(?Group, ?User, ?Delegatable) :-
      ?GRoot := rootPrincipal(?Group),
      ?GRoot: nestGroup(?Group, ?ToGroup, false), 
      ?ToGroupRoot := rootPrincipal(?ToGroup),
      ?ToGroupRoot: groupMember(?ToGroup, ?User, ?Delegatable).

//
//  These rules are redundant
//
//    nestGroup(?SrcGroup, ?DstGroup) :-
//      ?SrcOwner := rootPrincipal(?SrcGroup), ?SrcOwner: nestGroup(?SrcGroup, ?DstGroup).
//
//    nestGroup(?SrcGroup, ?DstGroup) :-
//      ?SrcOwner := rootPrincipal(?SrcGroup), ?SrcOwner: nestGroup(?SrcGroup, ?MidGroup),
//      nestGroup(?MidGroup, ?DstGroup).
//

    label('standard-membership-policy').
  }.

definit memberSet().


defcon checkDirectObject(?Dir, ?Name) :-
  spec("Check if a name represents a direct object"),
  {
    directObject(?Scid) :- ?NameAuthority := rootPrincipal($Dir), ?NameAuthority: nameObject($Name, ?Scid, $Dir).
    directObject(?Scid)?
    label("$Dir-$Name").
  }.

resolve(?Dir, ?Name, ?Scid, ?LastCertToken) :-
  singleComponent(?Name),
  ?RulesRef := checkDirectObject(?Dir, ?Name),
  ?DirAuthority := rootPrincipal(?Dir),
  ?ObjDelToken := label(?DirAuthority, "?Dir/?Name"),
  ?ScidSet := inferSet(?RulesRef, ?ObjDelToken),
  ?ObjScid := getNthArgFromSlogset(?ScidSet, 0),
  unify(?Scid, ?ObjScid),
  unify(?LastCertToken, ?ObjDelToken).

resolve(?Dir, ?Name, ?Scid, ?LastCertToken) :-
  splitHead(?Name, ?FirstComponent, ?Tail),
  ?RulesRef := checkDirectObject(?Dir, ?FirstComponent),
  ?DirAuthority := rootPrincipal(?Dir),
  ?ObjDelToken := label(?DirAuthority, "?Dir/?FirstComponent"),
  ?SubDirSet := inferSet(?RulesRef, ?ObjDelToken),
  ?SubDir := getNthArgFromSlogset(?SubDirSet, 0),
  resolve(?SubDir, ?Tail, ?Scid, ?LastCertToken).

defcon srnEvaluation() :-
  spec("Rules for SRN certified evaluation, in a bottom-up fashion"),
  {
    srnNameToID(?Name, ?Scid) :- 
      singleComponent(?Name), 
      ?RootAuthority := rootPrincipal($RootDir), 
      ?RootAuthority: nameObject(?Name, ?Scid, $RootDir).

    srnNameToID(?Name, ?Scid) :- 
      splitLast(?Name, ?Init, ?LastComponent), 
      srnNameToID(?Init, ?Dir), 
      ?DirAuthority := rootPrincipal(?Dir), 
      ?DirAuthority: nameObject(?LastComponent, ?Scid, ?Dir).

    srnIDPathTo(?UpstreamScid, ?DownstreamScid) :-
      ?UpstreamScid = ?DownstreamScid.

    srnIDPathTo(?UpstreamScid, ?DownstreamScid) :-
      ?UpstreamRoot := rootPrincipal(?UpstreamScid),
      ?UpstreamRoot: nameObject2(?UpstreamScid, ?Name, ?MiddleScid),
      srnIDPathTo(?MiddleScid, ?DownstreamScid).

    label("SRN-standard-evaluation").
  }.

definit srnEvaluation().

defcon accessPolicySet() :-
  spec("Policy for access control over named objects"),
  {
    allowAccess(?Group, ?Dir) :-
      ?Principal: accessToDir(?Group, ?Dir),
      ?DRoot := rootPrincipal(?Dir),
      ?Principal = ?DRoot.

    approveAccess(?P, ?Name) :-  
      srnNameToID(?Name, ?Scid),
      allowAccess(?Group, ?Dir),
      srnIDPathTo(?Dir, ?Scid),
      membership(?Group, ?P).

    label("standard-access-policy").
  }.

definit accessPolicySet().

defguard whoami() :-
  spec("Query principal id"),
  {
    principalId($Self).
    principalId(?X)?
  }.

defguard queryMembership(?Group, ?SubjectId) :-
  spec("Query the group membership of a principal"),
  ?MembershipPolicy := label('standard-membership-policy'),
  {
    link($MembershipPolicy).
    link($BearerRef).
    membership($Group, $SubjectId)?
  }.

defguard queryName(?Name) :-
  spec("Query a name"),
  resolve($RootDir, ?Name, ?Scid, ?LastCertToken),
  ?SRNEvalRulesRef := label("SRN-standard-evaluation"),
  ?ObjRoot := rootPrincipal(?Scid),
  ?ObjToken := label(?ObjRoot, ?Scid),
  {
    link($ObjToken).
    link($SRNEvalRulesRef).
    srnNameToID($Name, ?Scid)?
  }.

defguard queryLocalName(?Name) :-
  spec("Query a local name"),
  ?LocalObjectSet := label("localObjects/?Name"),
  {
    link($LocalObjectSet).
    localObject($Name, ?Scid)?
  }.

defguard accessNamedObject(?P, ?Name) :-
  spec("Check if a principal can access a named object"),
  resolve($RootDir, ?Name, ?Scid, ?LastCertToken),
  ?ObjRoot := rootPrincipal(?Scid),
  ?ObjToken := label(?ObjRoot, ?Scid),
  ?SRNEvalRulesRef := label("SRN-standard-evaluation"),
  ?MembershipPolicyRef := label("standard-membership-policy"),
  ?AccessPolicyRef := label("standard-access-policy"),
  {
    link($ObjToken).
    link($SRNEvalRulesRef).
    link($MembershipPolicyRef).
    link($AccessPolicyRef).
    link($BearerRef).
    approveAccess($P, $Name)?
  }.
