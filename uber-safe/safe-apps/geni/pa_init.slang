
defenv Selfie :-
  spec('Load the principal keypair'),
  principal('/home/vamsi/Code/safe/geni/pa_keypair.pem').

defcon makeMyIdSet() :-
  spec('Construct identity set for self'),
  {
    subject($SelfKey).
    encoding('safe').
    label().
  }.

defcon trustStructure() :-
  spec('Local policy for trust coordinators'),
  {
    identityProvider(IdP) :- geniRoot(Geni), Geni: identityProvider(IdP).
    sliceAuthority(SA) :- geniRoot(Geni), Geni: sliceAuthority(SA).
    projectAuthority(PA) :- geniRoot(Geni), Geni: projectAuthority(PA).
    geniProxy(GeniProxy) :- geniRoot(Geni), Geni: geniProxy(GeniProxy).
    aggregate(Agg) :- geniRoot(Geni), Geni: aggregate(Agg).
    gmoc(GeniMOC) :- geniRoot(Geni), Geni: gmoc(GeniMOC).
    label('subject/trust-structure').
  }.

defcon registeredUserPolicy() :-
  spec('Local policy for validating GENI users and PIs'),
  {
    geniUser(User) :- identityProvider(IdP), IdP: geniUser(User).
    geniPI(User) :- identityProvider(IdP), IdP: geniPI(User).
    label('subject/registered-user).
  }.

defpost bootstrap() :- [makeMyIdSet(), trustStructure(), registeredUserPolicy()].

definit bootstrap().
