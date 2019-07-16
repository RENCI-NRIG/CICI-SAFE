import("./stitch-all.slang").
//cannot use as tag here

defcon sdxTagAclEntry(?Tag,?Prefix) :-
  spec("user as tag access control list"),
  {
    sdxTagAclEntry($Tag,$Prefix).
    label("sdxTagAclEntry($Prefix)($Version)").
  }.

defpost postASTagAclEntry(?Tag,?Prefix) :- [sdxTagAclEntry(?Tag,?Prefix)].

defcon sdxTagAclEntrySD(?Tag, ?SrcPrefix, ?DstPrefix) :-
  spec("user as tag access control list"),
  {
    sdxTagAclEntry($Tag, $SrcPrefix, $DstPrefix).
    label("sdxTagAclEntry($DstPrefix)($Version)").
  }.

defcon sdPolicySet(?SrcPrefix,?DstPrefix) :-
  ?TagToken := label("sdxTagAclEntry(?DstPrefix)($Version)"),
  ?AuthASPolicy := label("authz as policy($PolicyVersion)"),
  ?TagPrivPolicy := label("tag-access-policy($PolicyVersion)"),
  {
    link($TagToken).
    link($AuthASPolicy).
    link($TagPrivPolicy).
    label("SD Policy Set/$SrcPrefix/$DstPrefix($Version)").
  }.

defpost postASTagAclEntrySD(?Tag,?SrcPrefix,?DstPrefix) :- [sdxTagAclEntrySD(?Tag,?SrcPrefix,?DstPrefix), sdPolicySet(?SrcPrefix,?DstPrefix)].

defcon authorizeASPolicy():-
  {

    authorizedAS(?AS, ?DstIP):-
      authorizedASByUserAttr(?AS, ?DstIP).

    authorizedASByUserAttr(?AS, ?Prefix):-
      sdxTagAclEntry(?Tag, ?PrefixSet),
      ?Prefix <: ?PrefixSet,
      tagPrivilege(?Tag, ?AS).

    authorizedAS(?AS, ?SrcIP, ?DstIP):-
      authorizedASByUserAttr(?AS, ?SrcIP, ?DstIP).

    authorizedASByUserAttr(?AS, ?SrcIP, ?DstIP):-
      sdxTagAclEntry(?Tag, ?SrcPrefix, ?DstPrefix),
      ?SrcIP <: ?SrcPrefix,
      ?DstIP <: ?DstPrefix,
      tagPrivilege(?Tag, ?AS).

    label("authz as policy($PolicyVersion)").
  }.

defpost postAuthorizeASPolicy() :- [authorizeASPolicy()].

defcon verifyASPolicy():-
  {
     verifyAS(?Owner, ?DstIP, ?AS):-
        ?Owner: authorizedAS(?AS, ?DstIP).

     verifyAS(?Owner, ?SrcIP, ?DstIP, ?AS):-
        ?Owner: authorizedAS(?AS, ?SrcIP, ?DstIP).

     label("verify as policy($PolicyVersion)").
  }.

definit verifyASPolicy().

defpost postVerifyASPolicy() :- [verifyASPolicy()].

defcon routingPolicy() :-
  {
    route(?Owner,?DstIP,?Path) :-
      authorizedAdvertise(?Owner,?DstIPRange,?Path,$Self),
      ?DstIP <: ?DstIPRange.

    authorizedAdvertise(?Owner,?DstIP,?Path,?AS):-
      eq([?Owner|?Tail],?Path),
      eq(?Tail,[]),
      ?Owner: advertise(?DstIP,?Path,?AS),
      ownPrefix(?Owner,?DstIP).

    authorizedAdvertise(?Owner,?DstIP,?Path,?AS):-
      eq([?Head|?Tail],?Path),
      ?Head:advertise(?DstIP,?Path,?AS),
      ?Owner: authorizedAS(?Head, ?DstIP),
      authorizedAdvertise(?Owner,?DstIPRange,?Tail,?Head),
      ?DstIP <: ?DstIPRange.

    route(?Owner,?SrcIP,?DstIP,?Path) :-
      authorizedAdvertise(?Owner,?SrcIPRange,?DstIPRange,?Path,$Self),
      ?SrcIP <: ?SrcIPRange,
      ?DstIP <: ?DstIPRange.

    authorizedAdvertise(?Owner,?SrcIP,?DstIP,?Path,?AS):-
      eq([?Owner|?Tail],?Path),
      eq(?Tail,[]),
      ?Owner: advertise(?SrcIP,?DstIP,?Path,?AS),
      ownPrefix(?Owner,?DstIP).

    authorizedAdvertise(?Owner,?SrcIP,?DstIP,?Path,?AS):-
      eq([?Head|?Tail],?Path),
      ?Head:advertise(?SrcIP,?DstIP,?Path,?AS),
      ?Owner: authorizedAS(?Head,?SrcIP,?DstIP),
      authorizedAdvertise(?Owner,?SrcIPRange,?DstIPRange,?Tail,?Head),
      ?SrcIP <: ?SrcIPRange,
      ?DstIP <: ?DstIPRange.

    compliantPath(?Owner,?SrcIP,?DstIP,?Path) :-
      eq([?Owner|?Tail],?Path),
      eq(?Tail,[]),
      ownPrefix(?Owner, ?DstIP).

    compliantPath(?Owner,?SrcIP,?DstIP,?Path) :-
      eq([?Owner|?Tail],?Path),
      compliantPath(?Owner, ?SrcIP, ?DstIP, ?Tail).

    compliantPath(?Owner, ?SrcIP, ?DstIP, ?Path) :-
      eq([?Head|?Tail],?Path),
      eq(?Tail,[]),
      ?Owner: authorizedAS(?Head, ?SrcIP, ?DstIP).

    compliantPath(?Owner, ?SrcIP, ?DstIP, ?Path) :-
      eq([?Head|?Tail],?Path),
      ?Owner: authorizedAS(?Head, ?SrcIP, ?DstIP),
      compliantPath(?Owner, ?SrcIP, ?DstIP, ?Tail).

    label("routing policy($PolicyVersion)").
  }.

defpost postRoutingPolicy() :- [routingPolicy()].
definit routingPolicy().

defcon advertiseRoute(?DstIP,?Path,?Target,?Src,?Len):-
  ?PathToken := label("Path/?Src/?DstIP/?Len ($Version)"),
  ?TagSubjectSet := label("tags($Version)"),
  {
    link($PathToken).
    link($TagSubjectSet).
    advertise($DstIP,$Path,$Target).
    label("Advertise/$Target/$DstIP/$Len ($Version)").
  }.

defpost postAdvertise(?DstIP,?Path,?Target,?Src,?Len):- [advertiseRoute(?DstIP,?Path,?Target,?Src,?Len)].

defcon advertiseRouteSD(?SrcIP,?DstIP,?Path,?Target,?Source,?Len):-
  ?PathToken := label("Path/?Source/?SrcIP/?DstIP/?Len ($Version)"),
  ?TagSubjectSet := label("tags($Version)"),
  {
    link($PathToken).
    link($TagSubjectSet).
    advertise($SrcIP,$DstIP,$Path,$Target).
    label("Advertise/$Target/$SrcIP/$DstIP/$Len ($Version)").
  }.

defpost postAdvertiseSD(?SrcIP,?DstIP,?Path,?Target,?Source,?Len):- [advertiseRouteSD(?SrcIP,?DstIP,?Path,?Target,?Source,?Len)].

//can not use init here
defcon startRoute(?DstIP,?Path,?Target,?Len):-
  ?Token := label("Token for IP allocation/?DstIP($Version)"),
  ?AuthzASPolicy := label("authz as policy($PolicyVersion)"),
  ?AuthzASACL := label("sdxTagAclEntry(?DstIP)($Version)"),
  ?TagPrivPolicy := label("tag-access-policy($PolicyVersion)"),
  {
    link($Token).
    link($AuthzASACL).
    link($AuthzASPolicy).
    link($TagPrivPolicy).
    advertise($DstIP,$Path,$Target).
    label("Advertise/$Target/$DstIP/$Len ($Version)").
  }.

defpost postInitRoute(?DstIP,?Path,?Target,?Len) :- [startRoute(?DstIP,?Path,?Target,?Len)].

defcon startRouteSD(?SrcIP,?DstIP,?Path,?Target,?Len):-
  ?Token := label("Token for IP allocation/?DstIP($Version)"),
  ?AuthzASPolicy := label("authz as policy($PolicyVersion)"),
  ?AuthzASACL := label("sdxTagAclEntry(?DstIP)($Version)"),
  ?TagPrivPolicy := label("tag-access-policy($PolicyVersion)"),
  {
    link($Token).
    link($AuthzASACL).
    link($AuthzASPolicy).
    link($TagPrivPolicy).
    advertise($SrcIP,$DstIP,$Path,$Target).
    label("Advertise/$Target/$SrcIP/$DstIP/$Len ($Version)").
  }.

defpost postInitRouteSD(?SrcIP,?DstIP,?Path,?Target,?Len) :- [startRouteSD(?SrcIP,?DstIP,?Path,?Target,?Len)].

defcon linkRoute(?Token,?DstIP,?Src,?Len):-
  spec("link a token for an advertised path"),
  {
    link($Token).
    label("Path/$Src/$DstIP/$Len ($Version)").
  }.

defcon addTokenPerIP(?Token,?DstIP):-
  {
    link($Token).
    label("Token for IP/$DstIP ($Version)").
  }.


defpost postPathToken(?Token,?DstIP,?Src,?Len) :- [linkRoute(?Token,?DstIP,?Src,?Len), addTokenPerIP(?Token,?DstIP)].

//void using Src and SrcIP at the same time, as the interpreter mistake $SrcIP for $Src + "IP"
defcon linkRouteSD(?Token,?SrcIP,?DstIP,?Source,?Len):-
  spec("link a token for an advertised path"),
  {
    link($Token).
    label("Path/$Source/$SrcIP/$DstIP/$Len ($Version)").
  }.

defcon addTokenPerIPSD(?Token,?SrcIP,?DstIP):-
  {
    link($Token).
    label("Token for IP/$SrcIP/$DstIP ($Version)").
  }.


defpost postPathTokenSD(?Token,?SrcIP,?DstIP,?Source,?Len) :- [linkRouteSD(?Token,?SrcIP,?DstIP,?Source,?Len), addTokenPerIPSD(?Token,?SrcIP,?DstIP)].

defguard verifyRoute(?Owner,?DstIP,?Path,?Token) :-
  ?RoutingPolicyRef := label("routing policy($PolicyVersion)"),
  ?OwnPrefixPolicy := label("own prefix policy($PolicyVersion)"),
  {
    link($OwnPrefixPolicy).
    link($RoutingPolicyRef).
    link($Token).
    route($Owner, $DstIP,$Path)?
  }.

defguard verifyRouteSD(?Owner,?SrcIP,?DstIP,?Path,?Token) :-
  ?RoutingPolicyRef := label("routing policy($PolicyVersion)"),
  ?OwnPrefixPolicy := label("own prefix policy($PolicyVersion)"),
  {
    link($OwnPrefixPolicy).
    link($RoutingPolicyRef).
    link($Token).
    route($Owner,$SrcIP,$DstIP,$Path)?
  }.

defguard verifyCompliantPath(?Owner,?SrcIP,?DstIP,?Path,?PolicyToken, ?RouteToken) :-
  ?RoutingPolicyRef := label("routing policy($PolicyVersion)"),
  ?OwnPrefixPolicy := label("own prefix policy($PolicyVersion)"),
  ?SelfTagSet := label("tags($Version)"),
  {
    link($OwnPrefixPolicy).
    link($RoutingPolicyRef).
    link($PolicyToken).
    link($RouteToken).
    link($SelfTagSet).
    compliantPath($Owner,$SrcIP,$DstIP,$Path)?
  }.

defguard verifyAS(?Owner, ?DstIP, ?AS, ?Token):-
  ?VerifyASPolicyRef := label("verify as policy($PolicyVersion)"),
  ?ASTagSet := label(?AS, "tags($Version)"),
  {
    link($Token).
    link($VerifyASPolicyRef).
    link($ASTagSet).
    verifyAS($Owner, $DstIP, $AS)?
  }.

defguard verifyASSD(?Owner, ?SrcIP, ?DstIP, ?AS, ?Token):-
  ?VerifyASPolicyRef := label("verify as policy($PolicyVersion)"),
  ?ASTagSet := label(?AS, "tags($Version)"),
  {
    link($Token).
    link($VerifyASPolicyRef).
    link($ASTagSet).
    verifyAS($Owner, $SrcIP, $DstIP, $AS)?
  }.

