//defenv Selfie() :-
//  spec('Load the principal keypair'),
//  principal($1).
//  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/registry.pem'). *)

defenv RootDir() :- u'IBpC0ljPorTuO85iJ-9GIxkXyDlOHAfFwcKfGtj-h2U:root'.

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
  spec("Add a link to a group's ID set. Invoked after delegation to this group"),
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

defcon delegateGroup(?Group, ?ToGroup, ?Delegatable) :-
  spec('Delegate administration to another group'), (* Membership is granted if membership of that group is granted *)
  ?GroupRef := label($Self, "groups/?Group"),
  {
    nestGroup($Group, $ToGroup, $Delegatable).
    link($GroupRef).
    label("delegateGroup/$Group/$ToGroup").  (* this link should be saved in the ID set of ?ToGroup *)
  }.

defpost postGroupDelegation(?Group, ?ToGroup, ?Delegatable) :- [delegateGroup(?Group, ?ToGroup, ?Delegatable)].


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


defcon mountObject(?ObjectName, ?Scid, ?MountScid) :-
  spec('Mount an object'),
  ?MountToken := label(?MountScid),
  {
    link($MountToken).
    nameObject($ObjectName, $Scid, $MountScid).
    label("$MountScid/$ObjectName").
  }.

defpost postObjectDelegation(?ObjectName, ?Scid, ?MountScid) :- [mountObject(?ObjectName, ?Scid, ?MountScid)].


defcon grantAccess(?Group, ?DirScid) :-
  spec('Grant group access to directory'),
  {
    accessPrefix($Group, $DirScid).
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
    ?Scid :- ?NameAuthority := rootId($Dir), ?NameAuthority: nameObject($Name, ?Scid, $Dir).
    ?Scid?
    label("$Dir-$Name").
  }.

resolve(?Dir, ?Name, ?Scid, ?LastCertToken) :-
  singleComponent(?Name),
  ?RulesRef := checkDirectObject(?Dir, ?Name),
  ?DirAuthority := rootPrincipal(?Dir),
  ?ObjDelToken := label(?DirAuthority, "?Dir/?Name"),
  ?ScidSet := inferSet(?RulesRef, ?ObjDelToken),
  ?ObjScid := reapId(?ScidSet),
  unify(?Scid, ?ObjScid),
  unify(?LastCertToken, ?ObjDelToken).

resolve(?Dir, ?Name, ?Scid, ?LastCertToken) :-
  splitHead(?Name, ?FirstComponent, ?Tail),
  ?RulesRef := checkDirectObject(?Dir, ?FirstComponent),
  ?DirAuthority := rootPrincipal(?Dir),
  ?ObjDelToken := label(?DirAuthority, "?Dir/?FirstComponent"),
  ?SubDirSet := inferSet(?RulesRef, ?ObjDelToken),
  ?SubDir := reapId(?SubDirSet),
  resolve(?SubDir, ?Tail, ?Scid, ?LastCertToken).

defcon SRNEvaluation() :-
  spec("Rules for SRN certified evaluation, in a bottom-up fashion"),
  {
    SRN(?Name, ?Scid) :- 
      singleComponent(?Name), 
      ?RootAuthority := rootPrincipal($RootDir), 
      ?RootAuthority: nameObject(?Name, ?Scid, $RootDir).

    SRN(?Name, ?Scid) :- 
      splitLast(?Name, ?Init, ?LastComponent), 
      SRN(?Init, ?Dir), 
      ?DirAuthority := rootPrincipal(?Dir), 
      ?DirAuthority: nameObject(?LastComponent, ?Scid, ?Dir).

    label("SRN-standard-evaluation").
  }.

definit SRNEvaluation().

defcon accessPolicySet() :-
  spec("Policy for access control over named objects"),
  {
    accessDir(?Group, ?Dir) :-
      ?Principal: accessPrefix(?Group, ?Dir),
      ?DRoot := rootPrincipal(?Dir),
      ?Principal = ?DRoot.

    approveAccess(?P, ?Name) :-  
      SRN(?Name, ?Scid),
      accessDir(?Group, ?Dir),
      membership(?Group, ?P).

    label("standard-access-policy").
  }.

definit accessPolicySet().


defguard queryMembership(?Group, ?SubjectId) :-
  spec("Query the group membership of a principal"),
  {
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
    SRN($Name, ?Scid)?
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
