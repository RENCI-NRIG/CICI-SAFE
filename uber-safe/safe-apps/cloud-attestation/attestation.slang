defenv CloudProvider :- 'us1lM2eXaliO5ISYkFEpdCV-m8E9BcS4XGoOBfg-Ook'.

defcon makeIdentitySet(?CN) :-
  spec("Construct the identity set for self"),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeIdentitySet(?CN)].


// Cloud provider: attests to instances

defcon launchInstance(?IP, ?ImgId, ?InstanceId) :- 
  spec("A cloud provider launches an instance with an image on an IP address"),
  ?ImageAuthority := rootPrincipal(?ImgId),
  ?ImageSet := label(?ImageAuthority, "images/?ImgId"), 
  {
    link($ImageSet).
    runs($IP, $ImgId).
    principalOnIP($InstanceId, $IP). 
    label("cloud/$IP").
  }. 

defpost postInstanceSet(?IP, ?ImgId, ?InstanceId) :- [launchInstance(?IP, ?ImgId, ?InstanceId)].

// Image owner calls this when it learns of a new certification of its image.
// Note: image should be identified by hash; then where are these certifications stored?
defcon addTokenToImageSet(?Token, ?ImgId) :-
  {
    link($Token).
    label("images/$ImgId").
  }.

defpost updateImageSet(?Token, ?ImgId) :- [addTokenToImageSet(?Token, ?ImgId)].


// Software-certifying third party: certify image properties

defcon certifyImage(?ImgId, ?Property) :-
  spec("A third party certifies a property of an image"),
  {
    hasImageProperty($ImgId, $Property).
    label("image-properties/$ImgId").
  }. 

defpost postImageProperty(?ImgId, ?Property) :- [certifyImage(?ImgId, ?Property)].


// Data owner: set up object ACLs based on image properties
defcon addObjectAcl(?ObjectId, ?Property) :-
  spec("A data owner adds an ACL to her object based on an image property"),
  {
     accessPrivilegeByProperty($Property, $ObjectId).
     label("acls/$ObjectId").
  }.

defpost postObjectAcl(?ObjectId, ?Property) :- [addObjectAcl(?ObjectId, ?Property)].

defcon addTrustedCertifier(?TrustedCertifierId) :-
  spec("A data owner adds a trusted software certifier"),
  {
    trustCertifier($TrustedCertifierId).
    label("trusted-certifiers").
  }.

defpost postTrustedCertifier(?TrustedCertifierId) :- [addTrustedCertifier(?TrustedCertifierId)].


defcon addTrustedCloudProvider(?TrustedCP) :-
  spec("A data owner adds a trusted cloud provider"),
  {
    trustCP($TrustedCP).
    label("trusted-CPs").
  }.

defpost postTrustedCP(?TrustedCP) :- [addTrustedCloudProvider(?TrustedCP)].

defcon objectPolicySet() :- 
  {
    approveAccess(?IP, ?ObjectId) :- 
      ?Owner := rootPrincipal(?ObjectId),
      ownerAcceptsAttestation(?Owner, ?IP, ?ImgId),
      ownerAcceptsImageProperty(?Owner, ?ImgId, ?Property),
      ?Owner: accessPrivilegeByProperty(?Property, ?ObjectId). 
  
    approveImageAccess(?ImgId, ?ObjectId) :- 
      ?Owner := rootPrincipal(?ObjectId),
      ownerAcceptsImageProperty(?Owner, ?ImgId, ?Property),
      ?Owner: accessPrivilegeByProperty(?Property, ?ObjectId). 
   
    ownerAcceptsAttestation(?Owner, ?IP, ?ImgId) :-
      ?CP: runs(?IP, ?ImgId),
      ?Owner: trustCP(?CP).

    ownerAcceptsImageProperty(?Owner, ?ImgId, ?Property) :-
       ?Certifier: hasImageProperty(?ImgId, ?Property),
       ?Owner: trustCertifier(?Certifier).

    label("standard-object-policy").
  }.

definit objectPolicySet().

// Guards: for a file server

//defguard accessObject(?IP, ?ObjectId) :-
//  ?PolicySet := label("standard-object-policy"),
//  ?Owner := rootPrincipal(?ObjectId),
//  ?ObjectAclSet := label(?Owner, "acls/?ObjectId"), 
//  ?TrustedCPSet := label(?Owner, "trusted-CPs"),
//  ?TrustedCertifierSet := label(?Owner, "trusted-certifiers"),
//  ?IPSet := label($CloudProvider, "cloud/?IP"),
//  {
//    link($PolicySet).
//    link($ObjectAclSet).
//    link($TrustedCPSet).
//    link($TrustedCertifierSet).
//    link($IPSet).
//    approveAccess($IP, $ObjectId)?
//  }.
//
//defguard accessObject(?IP, ?ObjectId) :-
//  {
//    approveAccess($IP, $ObjectId).
//    approveAccess($IP, $ObjectId)?
//  }.

defcon jumboContextForAccessingObject(?IP, ?ObjectId) :-
  ?PolicySet := label("standard-object-policy"),
  ?Owner := rootPrincipal(?ObjectId),
  ?ObjectAclSet := label(?Owner, "acls/?ObjectId"),
  ?TrustedCPSet := label(?Owner, "trusted-CPs"),
  ?TrustedCertifierSet := label(?Owner, "trusted-certifiers"),
  ?IPSet := label($CloudProvider, "cloud/?IP"),
  {
    link($PolicySet).
    link($ObjectAclSet).
    link($TrustedCPSet).
    link($TrustedCertifierSet).
    link($IPSet).
    label("jumbo-$IP-$ObjectId").
  }.

defpost makeContextSet(?IP, ?ObjectId) :- [jumboContextForAccessingObject(?IP, ?ObjectId)].

//defguard accessObject(?IP, ?ObjectId) :-
//  ?ContextSet := label("jumbo-?IP-?ObjectId"),
//  {
//    link($ContextSet).
//    approveAccess($IP, $ObjectId)?
//  }.

defguard accessObject(?IP, ?ObjectId) :-
  {
    link($BearerRef).
    approveAccess($IP, $ObjectId)?
  }.


defguard imgInstanceAccessesObject(?ImgId, ?ObjectId) :-
  ?PolicySet := label("standard-object-policy"),
  ?Owner := rootPrincipal(?ObjectId),
  ?ObjectAclSet := label(?Owner, "acls/?ObjectId"), 
  ?TrustedCPSet := label(?Owner, "trusted-CPs"),
  ?TrustedCertifierSet := label(?Owner, "trusted-certifiers"),
  ?ImgOwner := rootPrincipal(?ImgId),
  ?ImgSet := label(?ImgOwner, "images/?ImgId"), 
  {
    link($PolicySet).
    link($ObjectAclSet).
    link($TrustedCPSet).
    link($TrustedCertifierSet).
    link($ImgSet).
    approveImageAccess($ImgId, $ObjectId)?
  }.
