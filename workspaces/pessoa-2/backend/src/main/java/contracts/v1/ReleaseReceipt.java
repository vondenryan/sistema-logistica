package contracts.v1;

import java.math.*;
import java.time.*;
import java.util.*;

public record ReleaseReceipt(UUID routeId, boolean released) {}
